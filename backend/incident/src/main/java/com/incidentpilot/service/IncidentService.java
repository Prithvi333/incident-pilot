package com.incidentpilot.service;

import com.incidentpilot.client.AiServiceClient;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidTransitionException;
import event.EventType;
import event.IncidentDocumentIngestionEvent;
import com.incidentpilot.kafka.producer.IngestionEventProducer;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * Core business logic for incident CRUD and status transitions.
 *
 * <p>On creation: saves the incident, embeds it in the vector store for future RAG searches,
 * and logs an audit entry. On status change: validates the transition against the state machine,
 * records a timeline event, and logs an audit entry. All mutations are transactional.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final TimelineEventService timelineEventService;
    private final AuditService auditService;
    private final IngestionEventProducer ingestionEventProducer;
    private final AiServiceClient aiServiceClient;
    private final HttpServletRequest request;


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

        ingestionEventProducer.publish(IncidentDocumentIngestionEvent.builder()
                .id(saved.getId())
                .title(incident.getTitle())
                .eventType(EventType.INCIDENT)
                .serviceName(incident.getServiceName())
                .severityName(incident.getSeverity().name())
                .description(incident.getDescription())
                .build());
        auditService.log("CREATE", "Incident", saved.getId(), null, saved.getStatus().name());
        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        Incident incident = findOrThrow(id);
        return IncidentResponse.from(incident);
    }

    public RagResponse findSimilarIncidents(
            Long incidentId,
            String title,
            String description) {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication
                    .AuthenticationCredentialsNotFoundException(
                    "JWT token is missing");
        }

        String token = authorizationHeader.substring(7);

        return aiServiceClient.findSimilarIncidents(
                incidentId,
                title,
                description,
                token
        );
    }


    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getFilteredIncidents(IncidentStatus status, Severity severity, String search) {
        String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return incidentRepository.searchIncidents(status, severity, trimmedSearch).stream()
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
