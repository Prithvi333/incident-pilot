package com.incidentpilot.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.ingestion.kafka.producer.IngestionEventProducer;
import com.incidentpilot.ingestion.model.DocumentStatus;
import com.incidentpilot.ingestion.model.OutboxEvent;
import com.incidentpilot.ingestion.repository.DocumentRepository;
import com.incidentpilot.ingestion.repository.OutboxEventRepository;
import event.DocumentIngestionEvent;
import event.EventType;
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
 * Delivers pending document events to Kafka (at-least-once, per-document ordering, exponential backoff)
 * and keeps the document's ingestion status consistent with the delivery outcome:
 * <ul>
 *   <li>embed event accepted by Kafka → document {@code INDEXED};</li>
 *   <li>embed event undeliverable after {@value #MAX_ATTEMPTS} attempts → document {@code FAILED}.</li>
 * </ul>
 * A document's delete event is held back behind any earlier undelivered event of the same document,
 * so a late "embed" can never resurrect vectors of a deleted document.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final DocumentRepository documentRepository;
    private final IngestionEventProducer producer;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${outbox.relay-interval-ms:2000}")
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findTop20ByStatusOrderByIdAsc(OutboxEvent.Status.PENDING);
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
                DocumentIngestionEvent event = objectMapper.readValue(row.getPayload(), DocumentIngestionEvent.class);
                producer.publishSync(row.getAggregateKey(), event);
                tx.executeWithoutResult(s -> markSent(row.getId()));
            } catch (Exception e) {
                heldBack.add(row.getAggregateKey());
                log.warn("Outbox event {} ({}) not delivered yet: {}", row.getId(), row.getEventType(), e.getMessage());
                tx.executeWithoutResult(s -> markAttemptFailed(row.getId(), e));
            }
        }
    }

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
            if (EventType.DOCUMENT.name().equals(row.getEventType())) {
                updateDocumentStatus(row.getDocumentId(), DocumentStatus.INDEXED);
            }
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
                if (EventType.DOCUMENT.name().equals(row.getEventType())) {
                    updateDocumentStatus(row.getDocumentId(), DocumentStatus.FAILED);
                }
            } else {
                long seconds = Math.min(300, 2L << row.getAttempts());
                row.setNextAttemptAt(LocalDateTime.now().plus(Duration.ofSeconds(seconds)));
            }
        });
    }

    private void updateDocumentStatus(Long documentId, DocumentStatus status) {
        if (documentId == null) {
            return;
        }
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(status);
            if (status == DocumentStatus.INDEXED) {
                doc.setIndexedAt(LocalDateTime.now());
            }
        });
    }
}
