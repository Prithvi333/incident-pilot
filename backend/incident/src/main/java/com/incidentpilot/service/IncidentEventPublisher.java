package com.incidentpilot.service;

import com.incidentpilot.dto.IncidentEventMessage;
import com.incidentpilot.dto.IncidentEventType;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.model.Incident;
import com.incidentpilot.util.TransactionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcasts real-time incident events to the organization topic after the transaction commits.
 * The destination embeds the organization id, and subscriptions to it are only allowed for members
 * of that organization (see StompAuthChannelInterceptor).
 */
@Component
@RequiredArgsConstructor
public class IncidentEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public static String orgTopic(Long organizationId) {
        return "/topic/org." + organizationId + ".incidents";
    }

    public void publish(IncidentEventType type, Incident incident, String actor, Map<String, Object> data) {
        Long orgId = incident.getOrganization().getId();
        IncidentEventMessage message = IncidentEventMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .type(type)
                .incidentId(incident.getId())
                .organizationId(orgId)
                .actor(actor)
                .occurredAt(LocalDateTime.now())
                .incident(IncidentResponse.from(incident))
                .data(data)
                .build();

        TransactionUtils.afterCommit(() -> messagingTemplate.convertAndSend(orgTopic(orgId), message));
    }
}
