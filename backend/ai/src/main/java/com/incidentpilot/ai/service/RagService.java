package com.incidentpilot.ai.service;

import event.IncidentDocumentIngestionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RagService {

    private final VectorStore vectorStore;

    /**
     * Uses {@link ObjectProvider} instead of direct injection so the app starts
     * even when Qdrant or Bedrock is unreachable — graceful degradation over hard failure.
     */
    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      ObjectProvider<ChatModel> chatModelProvider) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();

        if (this.vectorStore == null) {
            log.warn("VectorStore not available — RAG embedding and search will be disabled");
        }

    }

    /** Embeds an incident's title, description, service, and severity into the vector store for future similarity search. */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedIncidentFallback")
    public void embedIncident(IncidentDocumentIngestionEvent incident) {
        if (vectorStore == null) {
            log.debug("Skipping embedIncident — VectorStore not configured");
            return;
        }
        String content = "Incident: " + incident.getTitle()
                + "\nService: " + incident.getServiceName()
                + "\nSeverity: " + incident.getServiceName()
                + (incident.getDescription() != null ? "\nDescription: " + incident.getDescription() : "");

        Document doc = new Document(content, Map.of(
                "type", "incident",
                "incidentId", String.valueOf(incident.getId()),
                "title", incident.getTitle(),
                "source", "incident:" + incident.getId()
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded incident #{} into vector store", incident.getId());
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedDocumentFallback")
    public void embedDocument(String text, String filename) {
        if (vectorStore == null) {
            log.debug("Skipping embedDocument — VectorStore not configured");
            return;
        }
        Document doc = new Document(text, Map.of(
                "type", "document",
                "source", filename
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded document '{}' into vector store", filename);
    }



    // ── Circuit breaker fallbacks ──

    public void embedIncidentFallback(IncidentDocumentIngestionEvent incident, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for incident #{}: {}", incident.getId(), ex.getMessage());
    }

    public void embedDocumentFallback(String text, String filename, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for document '{}': {}", filename, ex.getMessage());
    }


}

