package com.incidentpilot.service;

import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidTransitionException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final TimelineEventService timelineEventService;
    private final RagService ragService;
    private final AuditService auditService;

    /** Statuses that mean the incident is still active (not finished) */
    private static final Set<IncidentStatus> ACTIVE_STATUSES = Set.of(
            IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING
    );

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Incident incident = Incident.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .status(IncidentStatus.TRIGGERED)
                .assignee(request.getAssignee())
                .serviceName(request.getServiceName())
                .build();

        Incident saved = incidentRepository.save(incident);
        ragService.embedIncident(saved);
        auditService.log("CREATE", "Incident", saved.getId(), null, saved.getStatus().name());
        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        Incident incident = findOrThrow(id);
        return IncidentResponse.from(incident);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .map(IncidentResponse::from)
                .toList();
    }

    /**
     * Filter incidents by optional status and/or severity.
     * If both are null, returns all incidents (same as getAllIncidents).
     *
     * Why filter in the service layer instead of raw SQL?
     * We use JPA repository methods so the query is type-safe.
     * For more complex filtering, we'd upgrade to JPA Specifications.
     */
    @Transactional(readOnly = true)
    public List<IncidentResponse> getFilteredIncidents(IncidentStatus status, Severity severity) {
        List<Incident> incidents;

        if (status != null && severity != null) {
            incidents = incidentRepository.findByStatusAndSeverity(status, severity);
        } else if (status != null) {
            incidents = incidentRepository.findByStatus(status);
        } else if (severity != null) {
            incidents = incidentRepository.findBySeverity(severity);
        } else {
            incidents = incidentRepository.findAll();
        }

        return incidents.stream()
                .map(IncidentResponse::from)
                .toList();
    }

    /**
     * Transitions an incident to a new status after validating the move is allowed.
     *
     * Why validate here instead of the controller?
     * The service layer owns business logic. A controller should only handle
     * HTTP concerns (parsing requests, returning responses). If we later add
     * a WebSocket or CLI that also updates status, the validation still works.
     */
    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus newStatus) {
        Incident incident = findOrThrow(id);
        IncidentStatus oldStatus = incident.getStatus();

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new InvalidTransitionException(oldStatus, newStatus);
        }

        incident.setStatus(newStatus);

        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(LocalDateTime.now());
        }

        Incident updated = incidentRepository.save(incident);

        timelineEventService.recordStatusChange(updated, oldStatus, newStatus);
        auditService.log("STATUS_CHANGE", "Incident", updated.getId(), oldStatus.name(), newStatus.name());

        return IncidentResponse.from(updated);
    }

    /**
     * Compute aggregate stats across all incidents.
     * Average resolution time is calculated in Java (not SQL) to stay database-agnostic.
     */
    @Transactional(readOnly = true)
    public IncidentStatsResponse getStats() {
        List<Incident> all = incidentRepository.findAll();

        long total = all.size();
        long active = all.stream()
                .filter(i -> ACTIVE_STATUSES.contains(i.getStatus()))
                .count();
        long resolved = all.stream()
                .filter(i -> i.getResolvedAt() != null)
                .count();

        // Average resolution time: difference between createdAt and resolvedAt
        Double avgMinutes = all.stream()
                .filter(i -> i.getResolvedAt() != null && i.getCreatedAt() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getResolvedAt()).toMinutes())
                .average()
                .stream().boxed().findFirst().orElse(null);

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        Arrays.stream(Severity.values()).forEach(s ->
                bySeverity.put(s.name(), all.stream().filter(i -> i.getSeverity() == s).count()));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(IncidentStatus.values()).forEach(s ->
                byStatus.put(s.name(), all.stream().filter(i -> i.getStatus() == s).count()));

        return IncidentStatsResponse.builder()
                .totalIncidents(total)
                .activeIncidents(active)
                .resolvedIncidents(resolved)
                .averageResolutionMinutes(avgMinutes)
                .countBySeverity(bySeverity)
                .countByStatus(byStatus)
                .build();
    }

    private Incident findOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }
}
