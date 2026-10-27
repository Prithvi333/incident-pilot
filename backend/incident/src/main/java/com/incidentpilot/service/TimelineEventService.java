package com.incidentpilot.service;

import com.incidentpilot.dto.CreateTimelineEventRequest;
import com.incidentpilot.dto.TimelineEventResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.TimelineEvent;
import com.incidentpilot.model.TimelineEventType;
import com.incidentpilot.repository.IncidentRepository;
import com.incidentpilot.repository.TimelineEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimelineEventService {

    private final TimelineEventRepository timelineEventRepository;
    private final IncidentRepository incidentRepository;

    /**
     * Add a manual timeline entry (COMMENT or ACTION) to an incident.
     * Engineers use this during an outage to log what they're doing.
     */
    @Transactional
    public TimelineEventResponse addEvent(Long incidentId, CreateTimelineEventRequest request) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        TimelineEvent event = TimelineEvent.builder()
                .incident(incident)
                .type(request.getType())
                .message(request.getMessage())
                .author(request.getAuthor())
                .build();

        TimelineEvent saved = timelineEventRepository.save(event);
        return TimelineEventResponse.from(saved);
    }

    /**
     * Auto-generate a STATUS_CHANGE timeline entry.
     * Called internally by IncidentService when an incident transitions status.
     * Author is "system" because this is automatic, not a human action.
     */
    @Transactional
    public void recordStatusChange(Incident incident, IncidentStatus from, IncidentStatus to) {
        TimelineEvent event = TimelineEvent.builder()
                .incident(incident)
                .type(TimelineEventType.STATUS_CHANGE)
                .message("Status changed from " + from + " to " + to)
                .author("system")
                .build();

        timelineEventRepository.save(event);
    }

    /** Get full timeline for an incident, ordered oldest-first */
    @Transactional(readOnly = true)
    public List<TimelineEventResponse> getTimeline(Long incidentId) {
        // Verify the incident exists first
        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException(incidentId);
        }

        return timelineEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(TimelineEventResponse::from)
                .toList();
    }
}
