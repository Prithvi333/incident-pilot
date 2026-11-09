package com.incidentpilot.service;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Periodically checks open incidents for SLA breaches, records the breach once in the audit trail
 * and escalates it (real-time event + notifications to assignee, reporter and org admins).
 *
 * <p>SLA thresholds are severity-dependent: P1 gets 15 min to acknowledge / 4 hrs to resolve,
 * P4 gets 8 hrs / 7 days. The scheduler runs every 60 seconds (configurable via
 * {@code sla.check-interval-ms}). Each breach is flagged on the incident the first time it is
 * seen, so it is alerted exactly once (reopening an incident re-arms the resolve alert). Each
 * incident is processed in its own transaction, so one failure never blocks the others.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlaService {

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;
    private final IncidentActivityService activityService;
    private final PlatformTransactionManager transactionManager;

    /** Max time from creation to first acknowledgment, by severity. */
    private static final Map<Severity, Duration> ACKNOWLEDGE_SLA = Map.of(
            Severity.P1_CRITICAL, Duration.ofMinutes(15),
            Severity.P2_HIGH, Duration.ofMinutes(30),
            Severity.P3_MEDIUM, Duration.ofHours(2),
            Severity.P4_LOW, Duration.ofHours(8)
    );

    /** Max time from creation to resolution, by severity. */
    private static final Map<Severity, Duration> RESOLVE_SLA = Map.of(
            Severity.P1_CRITICAL, Duration.ofHours(4),
            Severity.P2_HIGH, Duration.ofHours(12),
            Severity.P3_MEDIUM, Duration.ofHours(48),
            Severity.P4_LOW, Duration.ofHours(168)
    );

    @Scheduled(fixedRateString = "${sla.check-interval-ms:60000}")
    public void checkSlaBreaches() {
        List<Long> openIds = incidentRepository.findIdsByStatusIn(
                Set.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        for (Long id : openIds) {
            try {
                tx.executeWithoutResult(status -> checkIncident(id));
            } catch (Exception e) {
                // e.g. optimistic-lock conflict with a concurrent update — retried on the next run
                log.warn("SLA check skipped for incident #{}: {}", id, e.getMessage());
            }
        }
    }

    private void checkIncident(Long id) {
        Incident incident = incidentRepository.findById(id).orElse(null);
        if (incident == null || incident.getOrganization() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        checkAcknowledgeSla(incident, now);
        checkResolveSla(incident, now);
    }

    private void checkAcknowledgeSla(Incident incident, LocalDateTime now) {
        if (incident.getStatus() != IncidentStatus.TRIGGERED || incident.isAckBreachNotified()) {
            return;
        }

        Duration sla = ACKNOWLEDGE_SLA.get(incident.getSeverity());
        Duration elapsed = Duration.between(incident.getCreatedAt(), now);

        if (elapsed.compareTo(sla) > 0) {
            incident.setAckBreachNotified(true);
            Incident saved = incidentRepository.saveAndFlush(incident);
            log.warn("SLA BREACH: Incident #{} ({}) not acknowledged within {} — elapsed {}",
                    saved.getId(), saved.getSeverity(), formatDuration(sla), formatDuration(elapsed));
            auditService.log(saved.getOrganization().getId(), "SLA_BREACH_ACK", "Incident", saved.getId(),
                    formatDuration(sla), formatDuration(elapsed));
            activityService.slaBreach(saved, "ACKNOWLEDGE", formatDuration(sla), formatDuration(elapsed));
        }
    }

    /** Applies to every open incident, including ones never acknowledged. */
    private void checkResolveSla(Incident incident, LocalDateTime now) {
        if (incident.isResolveBreachNotified()) {
            return;
        }

        Duration sla = RESOLVE_SLA.get(incident.getSeverity());
        Duration elapsed = Duration.between(incident.getCreatedAt(), now);

        if (elapsed.compareTo(sla) > 0) {
            incident.setResolveBreachNotified(true);
            Incident saved = incidentRepository.saveAndFlush(incident);
            log.warn("SLA BREACH: Incident #{} ({}) not resolved within {} — elapsed {}",
                    saved.getId(), saved.getSeverity(), formatDuration(sla), formatDuration(elapsed));
            auditService.log(saved.getOrganization().getId(), "SLA_BREACH_RESOLVE", "Incident", saved.getId(),
                    formatDuration(sla), formatDuration(elapsed));
            activityService.slaBreach(saved, "RESOLVE", formatDuration(sla), formatDuration(elapsed));
        }
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
