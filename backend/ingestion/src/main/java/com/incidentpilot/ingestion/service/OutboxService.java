package com.incidentpilot.ingestion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.ingestion.model.OutboxEvent;
import com.incidentpilot.ingestion.repository.OutboxEventRepository;
import event.DocumentIngestionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes document events to the outbox inside the caller's transaction (MANDATORY: never without one). */
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(DocumentIngestionEvent event) {
        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .organizationId(event.getOrganizationId())
                    .documentId(event.getDocumentId())
                    .eventType(event.getEventType().name())
                    .aggregateKey("document:" + event.getOrganizationId() + ":" + event.getDocumentId())
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event", e);
        }
    }
}
