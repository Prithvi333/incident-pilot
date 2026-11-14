package com.incidentpilot.ai.service;

import event.IncidentDocumentIngestionEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the Qdrant vector store in sync with MySQL, driven by Kafka events (MySQL and Qdrant do not
 * share a transaction, so synchronisation is event-based and every operation is idempotent).
 *
 * <ul>
 *   <li>Vectors get deterministic ids, so a redelivered or "updated" event overwrites the same point
 *       instead of creating a duplicate.</li>
 *   <li>Every vector carries {@code organizationId} so searches can be isolated per tenant.</li>
 *   <li>Failures are rethrown (never swallowed) so the Kafka listener retries; the producer side is a
 *       transactional outbox, so nothing is lost between MySQL and Kafka.</li>
 * </ul>
 */
@Service
@Slf4j
public class RagService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 150;
    private static final int BATCH_SIZE = 50;

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
     * Creates or updates an incident's vector (title, description, service, severity). Same point id
     * for the same incident, so incident edits update the existing point rather than adding a new one.
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

        Document doc = new Document(incidentPointId(incident.getOrganizationId(), incident.getId()), content, Map.of(
                "type", "incident",
                "organizationId", String.valueOf(incident.getOrganizationId()),
                "incidentId", String.valueOf(incident.getId()),
                "title", incident.getTitle(),
                "source", "incident:" + incident.getId()
        ));
        vectorStore.add(List.of(doc));
        log.info("Upserted incident #{} (org {}) in vector store", incident.getId(), incident.getOrganizationId());
    }

    /** Removes a deleted incident's vector so it can never be recommended. Deleting a missing point is a no-op. */
    public void deleteIncident(Long organizationId, Long incidentId) {
        if (vectorStore == null || organizationId == null || incidentId == null) {
            return;
        }
        vectorStore.delete(List.of(incidentPointId(organizationId, incidentId)));
        log.info("Deleted incident #{} (org {}) from vector store", incidentId, organizationId);
    }

    /** Convenience overload for callers without a knowledge-base document id. */
    public void embedDocument(String text, String filename, Long organizationId, String eventId) {
        embedDocument(text, filename, organizationId, eventId, null);
    }

    /**
     * Splits the document into overlapping chunks and stores each as its own vector. Chunk ids are
     * deterministic ({@code eventId + index}), so redelivery overwrites instead of duplicating, and
     * every chunk carries {@code documentId} so all of them can be deleted together.
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedDocumentFallback")
    public void embedDocument(String text, String filename, Long organizationId, String eventId, Long documentId) {
        if (vectorStore == null) {
            log.debug("Skipping embedDocument — VectorStore not configured");
            return;
        }
        if (organizationId == null) {
            log.warn("Skipping document event {} without organizationId", eventId);
            return;
        }
        List<String> chunks = chunk(text);
        List<Document> batch = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "document");
            metadata.put("organizationId", String.valueOf(organizationId));
            metadata.put("source", filename);
            metadata.put("chunk", String.valueOf(i));
            if (documentId != null) {
                metadata.put("documentId", String.valueOf(documentId));
            }
            batch.add(new Document(deterministicId("document:" + eventId + ":" + i), chunks.get(i), metadata));
            if (batch.size() == BATCH_SIZE) {
                vectorStore.add(List.copyOf(batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            vectorStore.add(List.copyOf(batch));
        }
        log.info("Embedded document '{}' (org {}) as {} chunk(s)", filename, organizationId, chunks.size());
    }

    /** Removes every chunk of a deleted knowledge-base document (matched by organization and document id). */
    public void deleteDocument(Long organizationId, Long documentId) {
        if (vectorStore == null || organizationId == null || documentId == null) {
            return;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        Filter.Expression filter = b.and(
                b.eq("organizationId", String.valueOf(organizationId)),
                b.eq("documentId", String.valueOf(documentId))).build();
        vectorStore.delete(filter);
        log.info("Deleted all chunks of document #{} (org {}) from vector store", documentId, organizationId);
    }

    /** Overlapping fixed-size chunks, cut at whitespace where possible. */
    static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + CHUNK_SIZE / 2) {
                    end = lastSpace;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private String incidentPointId(Long organizationId, Long incidentId) {
        return deterministicId("incident:" + organizationId + ":" + incidentId);
    }

    /** Same input always yields the same UUID, which makes re-processing an event idempotent. */
    private String deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    // ── Circuit breaker fallbacks: rethrow so the Kafka listener retries instead of losing the event ──

    public void embedIncidentFallback(IncidentDocumentIngestionEvent incident, Exception ex) {
        log.warn("Vector store unavailable — will retry incident #{}: {}", incident.getId(), ex.getMessage());
        throw rethrow(ex);
    }

    public void embedDocumentFallback(String text, String filename, Long organizationId, String eventId,
                                      Long documentId, Exception ex) {
        log.warn("Vector store unavailable — will retry document '{}': {}", filename, ex.getMessage());
        throw rethrow(ex);
    }

    public void embedDocumentFallback(String text, String filename, Long organizationId, String eventId, Exception ex) {
        log.warn("Vector store unavailable — will retry document '{}': {}", filename, ex.getMessage());
        throw rethrow(ex);
    }

    private RuntimeException rethrow(Exception ex) {
        return ex instanceof RuntimeException runtime ? runtime : new IllegalStateException(ex);
    }

}
