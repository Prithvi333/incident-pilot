package com.incidentpilot.controller;

import com.incidentpilot.dto.AuditLogResponse;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.dto.UpdateStatusRequest;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.service.AuditService;
import com.incidentpilot.service.IncidentService;
import com.incidentpilot.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for incident CRUD, status transitions, AI-powered similarity search, and audit trail.
 *
 * <p>Access control is handled at the security layer: VIEWER can read, ENGINEER can create/update.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final RagService ragService;
    private final AuditService auditService;

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List incidents with optional filters.
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
        List<IncidentResponse> incidents = incidentService.getFilteredIncidents(status, severity, search);
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/stats")
    public ResponseEntity<IncidentStatsResponse> getStats() {
        return ResponseEntity.ok(incidentService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    /** Uses AI (Bedrock) + vector search (Qdrant) to find similar past incidents and suggest resolutions. */
    @GetMapping("/{id}/similar")
    public ResponseEntity<RagResponse> getSimilarIncidents(@PathVariable Long id) {
        IncidentResponse incident = incidentService.getIncident(id);
        RagResponse response = ragService.findSimilarIncidents(id, incident.getTitle(), incident.getDescription());
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH (not PUT) because we're updating a single field (status), not the whole resource.
     * The state machine in the service layer validates the transition is allowed.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        IncidentResponse response = incidentService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<AuditLogResponse>> getAuditLog(@PathVariable Long id) {
        List<AuditLogResponse> logs = auditService.getLogsForEntity("Incident", id).stream()
                .map(AuditLogResponse::from)
                .toList();
        return ResponseEntity.ok(logs);
    }
}
