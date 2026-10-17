package com.incidentpilot.service;

import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidTransitionException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    // Constructor injection via @RequiredArgsConstructor — no @Autowired needed
    private final IncidentRepository incidentRepository;

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

        if (!incident.getStatus().canTransitionTo(newStatus)) {
            throw new InvalidTransitionException(incident.getStatus(), newStatus);
        }

        incident.setStatus(newStatus);

        // Record when the incident was actually resolved
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(LocalDateTime.now());
        }

        Incident updated = incidentRepository.save(incident);
        return IncidentResponse.from(updated);
    }

    private Incident findOrThrow(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
    }
}
