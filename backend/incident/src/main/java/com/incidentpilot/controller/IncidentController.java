package com.incidentpilot.controller;

import com.incidentpilot.dto.AssignIncidentRequest;
import com.incidentpilot.dto.AuditLogResponse;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.dto.RelatedIncidentResponse;
import com.incidentpilot.dto.UpdateIncidentRequest;
import com.incidentpilot.dto.UpdateStatusRequest;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.service.AuditService;
import com.incidentpilot.service.IncidentService;
import com.incidentpilot.service.IncidentSimilarityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for incident CRUD, assignment, status transitions, AI-powered similarity search and audit trail.
 *
 * <p>Access control is handled at the security layer: VIEWER can read, ENGINEER/ADMIN can create and
 * modify. Every endpoint only ever sees the caller's own organization.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final AuditService auditService;
    private final IncidentSimilarityService incidentSimilarityService;

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List incidents with optional filters, newest first.
     * Examples:
     *   GET /api/incidents                         → all
     *   GET /api/incidents?status=TRIGGERED         → only triggered
     *   GET /api/incidents?severity=P1_CRITICAL     → only P1
     *   GET /api/incidents?status=TRIGGERED&severity=P1_CRITICAL → both
     */
    @GetMapping
    public ResponseEntity<List<IncidentResponse>> getIncidents(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(incidentService.getFilteredIncidents(status, severity, search));
    }

    @GetMapping("/stats")
    public ResponseEntity<IncidentStatsResponse> getStats() {
        return ResponseEntity.ok(incidentService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    /** Uses the AI service (LLM + vector search) to find similar past incidents and suggest resolutions. */
    @GetMapping("/{id}/similar")
    public ResponseEntity<RagResponse> getSimilarIncidents(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.findSimilarIncidents(id));
    }

    /** Similar past incidents from the database only: works even when the AI service is down. */
    @GetMapping("/{id}/related")
    public ResponseEntity<List<RelatedIncidentResponse>> getRelatedIncidents(@PathVariable Long id) {
        return ResponseEntity.ok(incidentSimilarityService.findRelated(id));
    }

    /** Soft delete (ENGINEER/ADMIN): history is kept, the incident disappears from views and recommendations. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIncident(@PathVariable Long id) {
        incidentService.deleteIncident(id);
        return ResponseEntity.noContent().build();
    }

    /** Partial update of title, description, severity and service name. */
    @PatchMapping("/{id}")
    public ResponseEntity<IncidentResponse> updateIncident(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIncidentRequest request) {
        return ResponseEntity.ok(incidentService.updateIncident(id, request));
    }

    /**
     * PATCH (not PUT) because we're updating a single field (status), not the whole resource.
     * The state machine in the service layer validates the transition is allowed.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(incidentService.updateStatus(id, request.getStatus()));
    }

    /** Assigns the incident to a member of the caller's organization (blank assignee = unassign). */
    @PatchMapping("/{id}/assignee")
    public ResponseEntity<IncidentResponse> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignIncidentRequest request) {
        return ResponseEntity.ok(incidentService.assign(id, request.getAssignee()));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLogResponse>> getAuditLog(@PathVariable Long id) {
        // Tenant check first: a foreign incident id yields 404 before any audit data is read.
        incidentService.getIncident(id);
        Long orgId = CurrentUser.require().getOrganizationId();
        List<AuditLogResponse> logs = auditService.getLogsForEntity("Incident", id, orgId).stream()
                .map(AuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(logs);
    }
}
