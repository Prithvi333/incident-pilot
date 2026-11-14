package com.incidentpilot.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.kafka.producer.IngestionEventProducer;
import com.incidentpilot.model.OutboxEvent;
import com.incidentpilot.repository.OutboxEventRepository;
import event.IncidentDocumentIngestionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Publishes pending outbox rows to Kafka and marks them SENT once the broker acknowledges them.
 *
 * <ul>
 *   <li><b>At-least-once</b>: a crash between send and "mark sent" re-sends; the AI consumer is idempotent.</li>
 *   <li><b>Per-incident ordering</b>: rows are processed in id order and, when one fails or is waiting for
 *       its retry, later rows of the same incident are held back, so an old "update" can never overtake
 *       a newer "delete". The Kafka key is the incident, so one partition keeps the order downstream.</li>
 *   <li><b>Retries</b>: exponential backoff (4s, 8s, ... capped at 5 min); after {@value #MAX_ATTEMPTS}
 *       attempts the row is marked FAILED and logged for operator attention.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final IngestionEventProducer producer;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:2000}")
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxEvent.Status.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        LocalDateTime now = LocalDateTime.now();
        Set<String> heldBack = new HashSet<>();

        for (OutboxEvent row : pending) {
            if (heldBack.contains(row.getAggregateKey())) {
                continue;
            }
            if (row.getNextAttemptAt().isAfter(now)) {
                heldBack.add(row.getAggregateKey());
                continue;
            }
            try {
                IncidentDocumentIngestionEvent event =
                        objectMapper.readValue(row.getPayload(), IncidentDocumentIngestionEvent.class);
                producer.publishSync(row.getAggregateKey(), event);
                tx.executeWithoutResult(s -> markSent(row.getId()));
            } catch (Exception e) {
                heldBack.add(row.getAggregateKey());
                log.warn("Outbox event {} ({}) not delivered yet: {}", row.getId(), row.getEventType(), e.getMessage());
                tx.executeWithoutResult(s -> markAttemptFailed(row.getId(), e));
            }
        }
    }

    /** Housekeeping: delivered rows are only kept for a week. */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void purgeDelivered() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                outboxEventRepository.deleteSentBefore(LocalDateTime.now().minusDays(7)));
    }

    private void markSent(Long id) {
        outboxEventRepository.findById(id).ifPresent(row -> {
            row.setStatus(OutboxEvent.Status.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setLastError(null);
        });
    }

    private void markAttemptFailed(Long id, Exception error) {
        outboxEventRepository.findById(id).ifPresent(row -> {
            row.setAttempts(row.getAttempts() + 1);
            String message = String.valueOf(error.getMessage());
            row.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.setStatus(OutboxEvent.Status.FAILED);
                log.error("Outbox event {} ({}) permanently failed after {} attempts: {}",
                        row.getId(), row.getEventType(), row.getAttempts(), message);
            } else {
                long seconds = Math.min(300, 2L << row.getAttempts());
                row.setNextAttemptAt(LocalDateTime.now().plus(Duration.ofSeconds(seconds)));
            }
        });
    }
}
