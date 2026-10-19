package com.incidentpilot.service;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlaService {

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;

    private static final Map<Severity, Duration> ACKNOWLEDGE_SLA = Map.of(
            Severity.P1_CRITICAL, Duration.ofMinutes(15),
            Severity.P2_HIGH, Duration.ofMinutes(30),
            Severity.P3_MEDIUM, Duration.ofHours(2),
            Severity.P4_LOW, Duration.ofHours(8)
    );

    private static final Map<Severity, Duration> RESOLVE_SLA = Map.of(
            Severity.P1_CRITICAL, Duration.ofHours(4),
            Severity.P2_HIGH, Duration.ofHours(12),
            Severity.P3_MEDIUM, Duration.ofHours(48),
            Severity.P4_LOW, Duration.ofHours(168)
    );

    @Scheduled(fixedRateString = "${sla.check-interval-ms:60000}")
    public void checkSlaBreaches() {
        LocalDateTime now = LocalDateTime.now();

        List<Incident> openIncidents = incidentRepository.findByStatusIn(
                Set.of(IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING));

        for (Incident incident : openIncidents) {
            checkAcknowledgeSla(incident, now);
            checkResolveSla(incident, now);
        }
    }

    private void checkAcknowledgeSla(Incident incident, LocalDateTime now) {
        if (incident.getStatus() != IncidentStatus.TRIGGERED) {
            return;
        }

        Duration sla = ACKNOWLEDGE_SLA.get(incident.getSeverity());
        Duration elapsed = Duration.between(incident.getCreatedAt(), now);

        if (elapsed.compareTo(sla) > 0) {
            log.warn("SLA BREACH: Incident #{} ({}) not acknowledged within {} — elapsed {}",
                    incident.getId(), incident.getSeverity(), formatDuration(sla), formatDuration(elapsed));
            auditService.log("SLA_BREACH_ACK", "Incident", incident.getId(),
                    formatDuration(sla), formatDuration(elapsed));
        }
    }

    private void checkResolveSla(Incident incident, LocalDateTime now) {
        if (incident.getStatus() == IncidentStatus.TRIGGERED) {
            return;
        }

        Duration sla = RESOLVE_SLA.get(incident.getSeverity());
        Duration elapsed = Duration.between(incident.getCreatedAt(), now);

        if (elapsed.compareTo(sla) > 0) {
            log.warn("SLA BREACH: Incident #{} ({}) not resolved within {} — elapsed {}",
                    incident.getId(), incident.getSeverity(), formatDuration(sla), formatDuration(elapsed));
            auditService.log("SLA_BREACH_RESOLVE", "Incident", incident.getId(),
                    formatDuration(sla), formatDuration(elapsed));
        }
    }

    private String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
