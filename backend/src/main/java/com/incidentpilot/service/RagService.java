package com.incidentpilot.service;

import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.dto.SimilarMatch;
import com.incidentpilot.model.Incident;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) service for AI-powered incident resolution suggestions.
 *
 * <p>Combines two capabilities:
 * <ul>
 *   <li><b>Vector search</b> (Qdrant) — finds semantically similar past incidents and documents</li>   
 *   <li><b>LLM generation</b> (AWS Bedrock / Claude) — synthesizes resolution suggestions from context</li>
 * </ul>
 *
 * <p>Both dependencies are optional via {@link ObjectProvider}: if Qdrant is down, AI still
 * generates suggestions from the incident title alone. If Bedrock is unavailable, similar
 * matches are returned without an AI summary. All AI calls are wrapped in a Resilience4j
 * circuit breaker to prevent cascading failures.
 */
@Service
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    /**
     * Uses {@link ObjectProvider} instead of direct injection so the app starts
     * even when Qdrant or Bedrock is unreachable — graceful degradation over hard failure.
     */
    public RagService(ObjectProvider<VectorStore> vectorStoreProvider,
                      ObjectProvider<ChatModel> chatModelProvider) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.chatModel = chatModelProvider.getIfAvailable();
        if (this.vectorStore == null) {
            log.warn("VectorStore not available — RAG embedding and search will be disabled");
        }
        if (this.chatModel == null) {
            log.warn("ChatModel not available — AI suggestion generation will be disabled");
        }
    }

    /** Embeds an incident's title, description, service, and severity into the vector store for future similarity search. */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedIncidentFallback")
    public void embedIncident(Incident incident) {
        if (vectorStore == null) {
            log.debug("Skipping embedIncident — VectorStore not configured");
            return;
        }
        String content = "Incident: " + incident.getTitle()
                + "\nService: " + incident.getServiceName()
                + "\nSeverity: " + incident.getSeverity().name()
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

    /**
     * Finds similar incidents via vector search, then asks the LLM to suggest a resolution.
     *
     * <p>Works in three degraded modes:
     * <ol>
     *   <li>Both available → vector matches + AI suggestion based on context</li>
     *   <li>VectorStore down → AI suggestion based on incident title/description alone</li>
     *   <li>ChatModel down → similar matches returned, no AI summary</li>
     * </ol>
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "findSimilarFallback")
    public RagResponse findSimilarIncidents(Long incidentId, String title, String description) {
        List<Document> results = List.of();
        List<SimilarMatch> matches = List.of();

        if (vectorStore != null) {
            String query = title + (description != null ? " " + description : "");
            String excludeSource = "incident:" + incidentId;
            results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(6)
                            .similarityThreshold(0.5)
                            .build()
            ).stream()
                    .filter(doc -> !excludeSource.equals(doc.getMetadata().get("source")))
                    .limit(5)
                    .toList();
            matches = results.stream()
                    .map(doc -> SimilarMatch.builder()
                            .content(doc.getText())
                            .source(String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")))
                            .score(doc.getScore() != null ? doc.getScore() : 0.0)
                            .build())
                    .toList();
        }

        String suggestion = generateSuggestion(results, title, description);

        return RagResponse.builder()
                .similarMatches(matches)
                .resolutionSuggestion(suggestion)
                .aiAvailable(chatModel != null)
                .build();
    }

    private String generateSuggestion(List<Document> results, String title, String description) {
        if (chatModel == null) {
            if (results.isEmpty()) {
                return "No similar incidents found. AI suggestion generation is not available.";
            }
            return "Similar incidents found but AI suggestion generation is not available.";
        }

        String desc = description != null ? description : "No description provided";

        if (results.isEmpty()) {
            String prompt = """
                    Suggest a concise resolution strategy for this incident:
                    Title: %s
                    Description: %s

                    Provide 2-3 actionable steps. Be specific and concise.
                    """.formatted(title, desc);
            return chatModel.call(prompt);
        }

        String context = results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                Based on these similar past incidents and postmortems:

                %s

                Suggest a concise resolution strategy for this new incident:
                Title: %s
                Description: %s

                Provide 2-3 actionable steps. Be specific and concise.
                """.formatted(context, title, desc);

        return chatModel.call(prompt);
    }

    // ── Circuit breaker fallbacks ──

    public void embedIncidentFallback(Incident incident, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for incident #{}: {}", incident.getId(), ex.getMessage());
    }

    public void embedDocumentFallback(String text, String filename, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for document '{}': {}", filename, ex.getMessage());
    }

    public RagResponse findSimilarFallback(Long incidentId, String title, String description, Exception ex) {
        log.warn("AI service unavailable for similarity search: {}", ex.getMessage());
        return RagResponse.builder()
                .similarMatches(List.of())
                .resolutionSuggestion("AI service is currently unavailable. Please try again later.")
                .aiAvailable(false)
                .build();
    }
}
