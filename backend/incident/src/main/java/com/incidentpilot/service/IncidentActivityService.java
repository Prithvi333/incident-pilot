package com.incidentpilot.service;

import com.incidentpilot.dto.IncidentEventType;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.NotificationType;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.Severity;
import com.incidentpilot.model.TimelineEvent;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.TimelineEventRepository;
import com.incidentpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single place that decides, for each incident activity, which real-time event is broadcast and
 * who gets a notification. Called from inside the originating transaction.
 *
 * <p>Recipient rules (the actor and anyone outside the incident's organization are always excluded):
 * <ul>
 *   <li>created: org ADMINs + assignee</li>
 *   <li>assigned: new assignee; reassigned: previous assignee (told they were replaced)</li>
 *   <li>updated / status changed / resolved: assignee + reporter</li>
 *   <li>escalated / reopened / SLA breach: assignee + reporter + org ADMINs</li>
 *   <li>comment: assignee + reporter + earlier commenters</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class IncidentActivityService {

    private static final int COMMENT_PREVIEW_LENGTH = 500;

    private final IncidentEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final TimelineEventRepository timelineEventRepository;

    public void incidentCreated(Incident incident, String actor) {
        eventPublisher.publish(IncidentEventType.INCIDENT_CREATED, incident, actor, Map.of());
        notify(incident, NotificationType.INCIDENT_CREATED, actor,
                recipients(incident, true, false, incident.getAssignee()),
                "New incident: " + incident.getTitle(),
                actor + " reported a " + incident.getSeverity() + " incident on " + incident.getServiceName(),
                versionKey(incident));
    }

    public void assigned(Incident incident, String previousAssignee, String actor) {
        Map<String, Object> data = new HashMap<>();
        data.put("previousAssignee", previousAssignee);
        data.put("assignee", incident.getAssignee());
        eventPublisher.publish(IncidentEventType.INCIDENT_ASSIGNED, incident, actor, data);

        if (incident.getAssignee() != null) {
            notify(incident, NotificationType.INCIDENT_ASSIGNED, actor,
                    recipients(incident, false, false, incident.getAssignee()),
                    "Assigned to you: " + incident.getTitle(),
                    actor + " assigned you to INC-" + incident.getId(),
                    versionKey(incident));
        }
        if (previousAssignee != null && !previousAssignee.equals(incident.getAssignee())) {
            String now = incident.getAssignee() == null ? "unassigned" : "reassigned to " + incident.getAssignee();
            notify(incident, NotificationType.INCIDENT_REASSIGNED, actor,
                    recipients(incident, false, false, previousAssignee),
                    "No longer assigned: " + incident.getTitle(),
                    "INC-" + incident.getId() + " was " + now + " by " + actor,
                    versionKey(incident));
        }
    }

    public void updated(Incident incident, List<String> changedFields, String actor) {
        eventPublisher.publish(IncidentEventType.INCIDENT_UPDATED, incident, actor,
                Map.of("changedFields", changedFields));
        notify(incident, NotificationType.INCIDENT_UPDATED, actor,
                recipients(incident, false, true, incident.getAssignee()),
                "Incident updated: " + incident.getTitle(),
                actor + " changed " + String.join(", ", changedFields) + " on INC-" + incident.getId(),
                versionKey(incident));
    }

    public void escalated(Incident incident, Severity from, Severity to, List<String> changedFields, String actor) {
        Map<String, Object> data = new HashMap<>();
        data.put("fromSeverity", from.name());
        data.put("toSeverity", to.name());
        data.put("changedFields", changedFields);
        eventPublisher.publish(IncidentEventType.INCIDENT_ESCALATED, incident, actor, data);
        notify(incident, NotificationType.INCIDENT_ESCALATED, actor,
                recipients(incident, true, true, incident.getAssignee()),
                "Escalated to " + to + ": " + incident.getTitle(),
                actor + " raised INC-" + incident.getId() + " from " + from + " to " + to,
                versionKey(incident));
    }

    public void statusChanged(Incident incident, IncidentStatus from, IncidentStatus to, String actor) {
        Map<String, Object> data = Map.of("fromStatus", from.name(), "toStatus", to.name());

        if (IncidentStatus.isReopen(from, to)) {
            eventPublisher.publish(IncidentEventType.INCIDENT_REOPENED, incident, actor, data);
            notify(incident, NotificationType.INCIDENT_REOPENED, actor,
                    recipients(incident, true, true, incident.getAssignee()),
                    "Reopened: " + incident.getTitle(),
                    actor + " reopened INC-" + incident.getId() + " (back to " + to + ")",
                    versionKey(incident));
        } else if (to == IncidentStatus.RESOLVED) {
            eventPublisher.publish(IncidentEventType.INCIDENT_RESOLVED, incident, actor, data);
            notify(incident, NotificationType.INCIDENT_RESOLVED, actor,
                    recipients(incident, false, true, incident.getAssignee()),
                    "Resolved: " + incident.getTitle(),
                    actor + " resolved INC-" + incident.getId(),
                    versionKey(incident));
        } else {
            eventPublisher.publish(IncidentEventType.STATUS_CHANGED, incident, actor, data);
            notify(incident, NotificationType.STATUS_CHANGED, actor,
                    recipients(incident, false, true, incident.getAssignee()),
                    "Status changed: " + incident.getTitle(),
                    actor + " moved INC-" + incident.getId() + " from " + from + " to " + to,
                    versionKey(incident));
        }
    }

    public void commentAdded(Incident incident, TimelineEvent entry, String actor) {
        String preview = entry.getMessage().length() > COMMENT_PREVIEW_LENGTH
                ? entry.getMessage().substring(0, COMMENT_PREVIEW_LENGTH) + "…" : entry.getMessage();

        Map<String, Object> data = new HashMap<>();
        data.put("timelineEventId", entry.getId());
        data.put("entryType", entry.getType().name());
        data.put("author", entry.getAuthor());
        data.put("message", preview);
        eventPublisher.publish(IncidentEventType.COMMENT_ADDED, incident, actor, data);

        List<String> participants = new ArrayList<>(timelineEventRepository.findParticipantUsernames(incident.getId()));
        participants.add(incident.getAssignee());
        notify(incident, NotificationType.COMMENT_ADDED, actor,
                recipients(incident, false, true, participants.toArray(new String[0])),
                "New " + entry.getType().name().toLowerCase() + " on: " + incident.getTitle(),
                actor + ": " + preview,
                "c" + entry.getId());
    }

    /** SLA breaches are escalated to the assignee, the reporter and every organization admin. */
    public void slaBreach(Incident incident, String kind, String threshold, String elapsed) {
        Map<String, Object> data = Map.of("kind", kind, "threshold", threshold, "elapsed", elapsed);
        eventPublisher.publish(IncidentEventType.SLA_BREACH, incident, "system", data);
        String what = "ACKNOWLEDGE".equals(kind) ? "not acknowledged within " : "not resolved within ";
        notify(incident, NotificationType.SLA_BREACH, "system",
                recipients(incident, true, true, incident.getAssignee()),
                "SLA breach: " + incident.getTitle(),
                "INC-" + incident.getId() + " (" + incident.getSeverity() + ") " + what + threshold
                        + " — elapsed " + elapsed,
                kind + ":" + versionKey(incident));
    }

    // ── helpers ──

    private void notify(Incident incident, NotificationType type, String actor, Set<User> recipients,
                        String title, String message, String discriminator) {
        notificationService.notifyUsers(incident, type, actor, recipients, title, message, discriminator);
    }

    /** Distinct members of the incident's organization matching the requested roles/usernames. */
    private Set<User> recipients(Incident incident, boolean admins, boolean reporter,
                                 String... usernames) {
        Long orgId = incident.getOrganization().getId();
        Set<String> names = new LinkedHashSet<>();
        for (String name : usernames) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        if (reporter && incident.getCreatedBy() != null) {
            names.add(incident.getCreatedBy());
        }

        Set<User> result = new LinkedHashSet<>();
        if (!names.isEmpty()) {
            result.addAll(userRepository.findByOrganizationIdAndUsernameIn(orgId, names));
        }
        if (admins) {
            result.addAll(userRepository.findByOrganizationIdAndRole(orgId, Role.ADMIN));
        }
        return result;
    }

    private String versionKey(Incident incident) {
        return "v" + incident.getVersion();
    }
}
