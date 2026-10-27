package com.incidentpilot.ai.service;

import com.incidentpilot.ai.dto.RagResponse;
import com.incidentpilot.ai.dto.SimilarMatch;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IncidentAiService {
    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    public IncidentAiService(ObjectProvider<VectorStore> vectorStoreProvider,
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

    public RagResponse findSimilarFallback(Long incidentId, String title, String description, Exception ex) {
        log.warn("AI service unavailable for similarity search: {}", ex.getMessage());
        return RagResponse.builder()
                .similarMatches(List.of())
                .resolutionSuggestion("AI service is currently unavailable. Please try again later.")
                .aiAvailable(false)
                .build();
    }
}
