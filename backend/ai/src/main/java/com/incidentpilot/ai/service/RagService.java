package com.incidentpilot.ai.service;

import event.IncidentDocumentIngestionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class RagService {

    private final VectorStore vectorStore;

    /**
     * Uses {@link ObjectProvider} instead of direct injection so the app starts
     * even when Qdrant or the LLM is unreachable — graceful degradation over hard failure.
     */
    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      ObjectProvider<ChatModel> chatModelProvider) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();

        if (this.vectorStore == null) {
            log.warn("VectorStore not available — RAG embedding and search will be disabled");
        }

    }

    /**
     * Embeds an incident's title, description, service, and severity into the vector store for future
     * similarity search. The vector is tagged with the owning organization so searches can be
     * isolated per tenant, and it gets a deterministic id so a redelivered Kafka message updates the
     * same vector instead of creating a duplicate.
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedIncidentFallback")
    public void embedIncident(IncidentDocumentIngestionEvent incident) {
        if (vectorStore == null) {
            log.debug("Skipping embedIncident — VectorStore not configured");
            return;
        }
        if (incident.getOrganizationId() == null) {
            log.warn("Skipping incident event {} without organizationId", incident.getEventId());
            return;
        }
        String content = "Incident: " + incident.getTitle()
                + "\nService: " + incident.getServiceName()
                + "\nSeverity: " + incident.getSeverityName()
                + (incident.getDescription() != null ? "\nDescription: " + incident.getDescription() : "");

        String id = deterministicId("incident:" + incident.getOrganizationId() + ":" + incident.getId());
        Document doc = new Document(id, content, Map.of(
                "type", "incident",
                "organizationId", String.valueOf(incident.getOrganizationId()),
                "incidentId", String.valueOf(incident.getId()),
                "title", incident.getTitle(),
                "source", "incident:" + incident.getId()
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded incident #{} (org {}) into vector store", incident.getId(), incident.getOrganizationId());
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedDocumentFallback")
    public void embedDocument(String text, String filename, Long organizationId, String eventId) {
        if (vectorStore == null) {
            log.debug("Skipping embedDocument — VectorStore not configured");
            return;
        }
        if (organizationId == null) {
            log.warn("Skipping document event {} without organizationId", eventId);
            return;
        }
        Document doc = new Document(deterministicId("document:" + eventId), text, Map.of(
                "type", "document",
                "organizationId", String.valueOf(organizationId),
                "source", filename
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded document '{}' (org {}) into vector store", filename, organizationId);
    }

    /** Same input always yields the same UUID, which makes re-processing an event idempotent. */
    private String deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ── Circuit breaker fallbacks ──

    public void embedIncidentFallback(IncidentDocumentIngestionEvent incident, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for incident #{}: {}", incident.getId(), ex.getMessage());
    }

    public void embedDocumentFallback(String text, String filename, Long organizationId, String eventId, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for document '{}': {}", filename, ex.getMessage());
    }

}
