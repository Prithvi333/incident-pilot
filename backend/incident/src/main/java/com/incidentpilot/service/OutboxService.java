package com.incidentpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.OutboxEvent;
import com.incidentpilot.repository.OutboxEventRepository;
import event.EventType;
import event.IncidentDocumentIngestionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records "this incident's embedding must be created/updated/removed" in the outbox, inside the
 * caller's transaction (MANDATORY: it must never be called without one).
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /** Create or update the incident's vector. Idempotent in the AI service (deterministic point id). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueUpsert(Incident incident) {
        enqueue(incident, EventType.INCIDENT);
    }

    /** Remove the incident's vector so it can never be recommended again. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueDelete(Incident incident) {
        enqueue(incident, EventType.INCIDENT_DELETE);
    }

    private void enqueue(Incident incident, EventType type) {
        Long orgId = incident.getOrganization().getId();
        IncidentDocumentIngestionEvent event = IncidentDocumentIngestionEvent.builder()
                .eventType(type)
                .organizationId(orgId)
                .id(incident.getId())
                .title(incident.getTitle())
                .serviceName(incident.getServiceName())
                .severityName(incident.getSeverity().name())
                .description(incident.getDescription())
                .build();
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .organizationId(orgId)
                    .eventType(type.name())
                    .aggregateKey("incident:" + orgId + ":" + incident.getId())
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            // cannot happen for this simple event; failing the transaction is the safe choice
            throw new IllegalStateException("Could not serialize outbox event", e);
        }
    }
}
