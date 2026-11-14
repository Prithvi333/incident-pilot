package com.incidentpilot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.incidentpilot.client.AiServiceClient;
import com.incidentpilot.config.TenantCache;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.dto.SimilarMatch;
import com.incidentpilot.dto.UpdateIncidentRequest;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidAssigneeException;
import com.incidentpilot.exception.InvalidTransitionException;
import com.incidentpilot.exception.NoChangesException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import com.incidentpilot.repository.OrganizationRepository;
import com.incidentpilot.repository.UserRepository;
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.security.TenantPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Core business logic for incident CRUD, assignment and status transitions.
 *
 * <p>Every operation is scoped to the caller's organization (taken from the authenticated
 * principal): an incident id from another organization behaves exactly like a missing id. All
 * mutations are transactional and report to {@link IncidentActivityService}, which broadcasts the
 * real-time event and creates notifications once the transaction commits.
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final TimelineEventService timelineEventService;
    private final AuditService auditService;
    private final IncidentActivityService activityService;
    private final OutboxService outboxService;
    private final TenantCache cache;
    private final AiServiceClient aiServiceClient;
    private final HttpServletRequest request;

    /** Statuses that mean the incident is still active (not finished) */
    private static final Set<IncidentStatus> ACTIVE_STATUSES = Set.of(
            IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING
    );

    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        TenantPrincipal me = CurrentUser.require();
        Organization org = organizationRepository.getReferenceById(me.getOrganizationId());
        String assignee = resolveAssignee(request.getAssignee(), me.getOrganizationId());

        Incident saved = incidentRepository.save(Incident.builder()
                .organization(org)
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .status(IncidentStatus.TRIGGERED)
                .assignee(assignee)
                .serviceName(request.getServiceName().trim())
                .createdBy(me.getUsername())
                .build());

        // Same transaction as the incident row: the embedding event can never be lost or orphaned
        outboxService.enqueueUpsert(saved);
        cache.invalidateIncident(me.getOrganizationId(), saved.getId());
        auditService.log(me.getOrganizationId(), "CREATE", "Incident", saved.getId(), null, saved.getStatus().name());
        activityService.incidentCreated(saved, me.getUsername());
        return IncidentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long id) {
        Long orgId = CurrentUser.require().getOrganizationId();
        return cache.get(orgId, "incident", String.valueOf(id), TenantCache.DETAIL_TTL,
                new TypeReference<IncidentResponse>() { },
                () -> IncidentResponse.from(findOrThrow(id)));
    }

    /**
     * Asks the AI service for similar incidents. The incident is loaded through the tenant-scoped
     * lookup first, so a foreign id never reaches the AI service; the AI service additionally
     * filters its vector search by the organization in the forwarded JWT.
     */
    public RagResponse findSimilarIncidents(Long incidentId) {
        IncidentResponse incident = getIncident(incidentId);

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new org.springframework.security.authentication
                    .AuthenticationCredentialsNotFoundException("JWT token is missing");
        }
        String token = authorizationHeader.substring(7);

        return withoutDeletedIncidents(
                aiServiceClient.findSimilarIncidents(incidentId, incident.getTitle(), incident.getDescription(), token));
    }

    /**
     * Vector sync is asynchronous, so a just-deleted incident could still be in Qdrant for a moment.
     * Matches that point to incidents which are no longer active in this organization are dropped here,
     * so deleted incidents never show up in recommendations regardless of sync lag.
     */
    private RagResponse withoutDeletedIncidents(RagResponse response) {
        if (response == null || response.getSimilarMatches() == null || response.getSimilarMatches().isEmpty()) {
            return response;
        }
        Set<Long> referenced = new java.util.HashSet<>();
        for (SimilarMatch match : response.getSimilarMatches()) {
            Long id = incidentIdOf(match.getSource());
            if (id != null) {
                referenced.add(id);
            }
        }
        if (referenced.isEmpty()) {
            return response;
        }
        Set<Long> active = new java.util.HashSet<>(
                incidentRepository.findActiveIds(CurrentUser.require().getOrganizationId(), referenced));
        response.setSimilarMatches(response.getSimilarMatches().stream()
                .filter(m -> {
                    Long id = incidentIdOf(m.getSource());
                    return id == null || active.contains(id);
                })
                .toList());
        return response;
    }

    private Long incidentIdOf(String source) {
        if (source != null && source.startsWith("incident:")) {
            try {
                return Long.parseLong(source.substring("incident:".length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getFilteredIncidents(IncidentStatus status, Severity severity, String search) {
        Long orgId = CurrentUser.require().getOrganizationId();
        String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        String filterKey = status + "|" + severity + "|" + TenantCache.hash(trimmedSearch);
        return cache.get(orgId, "list", filterKey, TenantCache.LIST_TTL,
                new TypeReference<List<IncidentResponse>>() { },
                () -> incidentRepository.searchIncidents(orgId, status, severity, trimmedSearch).stream()
                        .map(IncidentResponse::from)
                        .toList());
    }

    /**
     * Transitions an incident to a new status after validating the move is allowed.
     * RESOLVED → INVESTIGATING is a reopen: it clears resolvedAt and re-arms the resolve-SLA alert.
     *
     * <p>Validation lives in the service (not the controller) so any future entry point
     * (WebSocket command, CLI) gets the same rules. The entity's @Version makes two concurrent
     * transitions fail with a 409 instead of silently overwriting each other.
     */
    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus newStatus) {
        TenantPrincipal me = CurrentUser.require();
        Incident incident = findOrThrow(id);
        IncidentStatus oldStatus = incident.getStatus();

        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new InvalidTransitionException(oldStatus, newStatus);
        }

        incident.setStatus(newStatus);
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(LocalDateTime.now());
        }
        if (IncidentStatus.isReopen(oldStatus, newStatus)) {
            incident.setResolvedAt(null);
            incident.setResolveBreachNotified(false);
        }

        Incident updated = incidentRepository.saveAndFlush(incident);

        timelineEventService.recordStatusChange(updated, oldStatus, newStatus, me.getUsername());
        auditService.log(me.getOrganizationId(), "STATUS_CHANGE", "Incident", updated.getId(),
                oldStatus.name(), newStatus.name());
        activityService.statusChanged(updated, oldStatus, newStatus, me.getUsername());
        cache.invalidateIncident(me.getOrganizationId(), updated.getId());

        return IncidentResponse.from(updated);
    }

    /** Assigns (or unassigns, with a blank value) the incident to a member of the caller's organization. */
    @Transactional
    public IncidentResponse assign(Long id, String assigneeUsername) {
        TenantPrincipal me = CurrentUser.require();
        Incident incident = findOrThrow(id);
        String newAssignee = resolveAssignee(assigneeUsername, me.getOrganizationId());
        String previous = incident.getAssignee();

        if (Objects.equals(previous, newAssignee)) {
            return IncidentResponse.from(incident);
        }

        incident.setAssignee(newAssignee);
        Incident updated = incidentRepository.saveAndFlush(incident);

        timelineEventService.recordSystemNote(updated, me.getUsername(),
                newAssignee == null ? "Unassigned (was " + previous + ")"
                        : previous == null ? "Assigned to " + newAssignee
                        : "Reassigned from " + previous + " to " + newAssignee);
        auditService.log(me.getOrganizationId(), "ASSIGN", "Incident", updated.getId(), previous, newAssignee);
        activityService.assigned(updated, previous, me.getUsername());
        cache.invalidateIncident(me.getOrganizationId(), updated.getId());
        return IncidentResponse.from(updated);
    }

    /**
     * Partial update of title, description, severity and service name. Raising the severity
     * (e.g. P3 → P2) counts as an escalation and notifies admins; the stricter SLA of the new
     * severity applies from then on.
     */
    @Transactional
    public IncidentResponse updateIncident(Long id, UpdateIncidentRequest request) {
        TenantPrincipal me = CurrentUser.require();
        Incident incident = findOrThrow(id);

        List<String> changed = new ArrayList<>();
        Severity oldSeverity = incident.getSeverity();

        if (request.getTitle() != null && !request.getTitle().trim().equals(incident.getTitle())) {
            incident.setTitle(request.getTitle().trim());
            changed.add("title");
        }
        if (request.getDescription() != null && !request.getDescription().equals(incident.getDescription())) {
            incident.setDescription(request.getDescription());
            changed.add("description");
        }
        if (request.getServiceName() != null && !request.getServiceName().trim().equals(incident.getServiceName())) {
            incident.setServiceName(request.getServiceName().trim());
            changed.add("serviceName");
        }
        if (request.getSeverity() != null && request.getSeverity() != oldSeverity) {
            incident.setSeverity(request.getSeverity());
            changed.add("severity");
        }

        if (request.getTitle() == null && request.getDescription() == null
                && request.getServiceName() == null && request.getSeverity() == null) {
            throw new NoChangesException();
        }
        if (changed.isEmpty()) {
            return IncidentResponse.from(incident);
        }

        Incident updated = incidentRepository.saveAndFlush(incident);

        if (changed.contains("severity")) {
            auditService.log(me.getOrganizationId(), "SEVERITY_CHANGE", "Incident", updated.getId(),
                    oldSeverity.name(), updated.getSeverity().name());
        }
        auditService.log(me.getOrganizationId(), "UPDATE", "Incident", updated.getId(), null, String.join(",", changed));

        // Semantic content changed: refresh the vector (same point id, so no duplicate in Qdrant)
        outboxService.enqueueUpsert(updated);
        cache.invalidateIncident(me.getOrganizationId(), updated.getId());

        // P1 is the lowest ordinal, so a lower ordinal means a more severe incident.
        boolean escalated = changed.contains("severity") && updated.getSeverity().ordinal() < oldSeverity.ordinal();
        if (escalated) {
            timelineEventService.recordSystemNote(updated, me.getUsername(),
                    "Severity escalated from " + oldSeverity + " to " + updated.getSeverity());
            activityService.escalated(updated, oldSeverity, updated.getSeverity(), changed, me.getUsername());
        } else {
            activityService.updated(updated, changed, me.getUsername());
        }
        return IncidentResponse.from(updated);
    }

    /**
     * Aggregate stats for the caller's organization only.
     * Average resolution time is calculated in Java (not SQL) to stay database-agnostic.
     */
    @Transactional(readOnly = true)
    public IncidentStatsResponse getStats() {
        Long orgId = CurrentUser.require().getOrganizationId();
        return cache.get(orgId, "stats", "all", TenantCache.STATS_TTL,
                new TypeReference<IncidentStatsResponse>() { }, () -> computeStats(orgId));
    }

    private IncidentStatsResponse computeStats(Long orgId) {
        List<Incident> all = incidentRepository.findByOrganizationId(orgId);

        long total = all.size();
        long active = all.stream().filter(i -> ACTIVE_STATUSES.contains(i.getStatus())).count();
        long resolved = all.stream().filter(i -> i.getResolvedAt() != null).count();

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

    /**
     * Soft delete: the row, timeline and audit history are kept, but the incident disappears from every
     * query, the dashboard and (via the outbox) from the vector store. Cache entries are invalidated.
     */
    @Transactional
    public void deleteIncident(Long id) {
        TenantPrincipal me = CurrentUser.require();
        Incident incident = findOrThrow(id);

        incident.setDeletedAt(LocalDateTime.now());
        incident.setDeletedBy(me.getUsername());
        Incident deleted = incidentRepository.saveAndFlush(incident);

        timelineEventService.recordSystemNote(deleted, me.getUsername(), "Incident deleted");
        auditService.log(me.getOrganizationId(), "DELETE", "Incident", deleted.getId(), deleted.getStatus().name(), null);
        outboxService.enqueueDelete(deleted);
        activityService.deleted(deleted, me.getUsername());
        cache.invalidateIncident(me.getOrganizationId(), deleted.getId());
    }

    /** Tenant-scoped lookup: an id that belongs to another organization is reported as not found. */
    Incident findOrThrow(Long id) {
        Long orgId = CurrentUser.require().getOrganizationId();
        return incidentRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }

    /** Blank means unassigned; otherwise the username must belong to the caller's organization. */
    private String resolveAssignee(String assignee, Long organizationId) {
        if (assignee == null || assignee.isBlank()) {
            return null;
        }
        String username = assignee.trim();
        return userRepository.findByOrganizationIdAndUsername(organizationId, username)
                .map(u -> u.getUsername())
                .orElseThrow(() -> new InvalidAssigneeException(username));
    }
}
