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
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.security.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Timeline entries for an incident. All lookups are scoped to the caller's organization. */
@Service
@RequiredArgsConstructor
public class TimelineEventService {

    private final TimelineEventRepository timelineEventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentActivityService activityService;

    /**
     * Add a manual timeline entry (COMMENT or ACTION) to an incident.
     * The author is the authenticated user — never a value supplied by the client.
     */
    @Transactional
    public TimelineEventResponse addEvent(Long incidentId, CreateTimelineEventRequest request) {
        TenantPrincipal me = CurrentUser.require();
        Incident incident = findIncident(incidentId, me.getOrganizationId());

        TimelineEvent saved = timelineEventRepository.save(TimelineEvent.builder()
                .incident(incident)
                .type(request.getType())
                .message(request.getMessage())
                .author(me.getUsername())
                .build());

        activityService.commentAdded(incident, saved, me.getUsername());
        return TimelineEventResponse.from(saved);
    }

    /** Auto-generate a STATUS_CHANGE timeline entry attributed to whoever made the change. */
    @Transactional
    public void recordStatusChange(Incident incident, IncidentStatus from, IncidentStatus to, String actor) {
        String message = IncidentStatus.isReopen(from, to)
                ? "Incident reopened (status changed from " + from + " to " + to + ")"
                : "Status changed from " + from + " to " + to;
        recordSystemNote(incident, actor, message, TimelineEventType.STATUS_CHANGE);
    }

    /** Auto-generated note for assignment/escalation changes. */
    @Transactional
    public void recordSystemNote(Incident incident, String actor, String message) {
        recordSystemNote(incident, actor, message, TimelineEventType.ACTION);
    }

    private void recordSystemNote(Incident incident, String actor, String message, TimelineEventType type) {
        timelineEventRepository.save(TimelineEvent.builder()
                .incident(incident)
                .type(type)
                .message(message)
                .author(actor)
                .build());
    }

    /** Get full timeline for an incident, ordered oldest-first */
    @Transactional(readOnly = true)
    public List<TimelineEventResponse> getTimeline(Long incidentId) {
        TenantPrincipal me = CurrentUser.require();
        findIncident(incidentId, me.getOrganizationId());

        return timelineEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .map(TimelineEventResponse::from)
                .toList();
    }

    private Incident findIncident(Long incidentId, Long organizationId) {
        return incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
    }
}
