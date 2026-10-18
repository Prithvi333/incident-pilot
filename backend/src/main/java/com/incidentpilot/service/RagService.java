package com.incidentpilot.service;

import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.dto.SimilarMatch;
import com.incidentpilot.model.Incident;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedIncidentFallback")
    public void embedIncident(Incident incident) {
        String content = "Incident: " + incident.getTitle()
                + "\nService: " + incident.getServiceName()
                + "\nSeverity: " + incident.getSeverity().name()
                + (incident.getDescription() != null ? "\nDescription: " + incident.getDescription() : "");

        Document doc = new Document(content, Map.of(
                "type", "incident",
                "incidentId", incident.getId(),
                "title", incident.getTitle(),
                "source", "incident:" + incident.getId()
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded incident #{} into vector store", incident.getId());
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "embedDocumentFallback")
    public void embedDocument(String text, String filename) {
        Document doc = new Document(text, Map.of(
                "type", "document",
                "source", filename
        ));
        vectorStore.add(List.of(doc));
        log.info("Embedded document '{}' into vector store", filename);
    }

    @CircuitBreaker(name = "ai-service", fallbackMethod = "findSimilarFallback")
    public RagResponse findSimilarIncidents(String title, String description) {
        String query = title + (description != null ? " " + description : "");

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()
        );

        List<SimilarMatch> matches = results.stream()
                .map(doc -> SimilarMatch.builder()
                        .content(doc.getText())
                        .source(String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")))
                        .score(doc.getScore() != null ? doc.getScore() : 0.0)
                        .build())
                .toList();

        String suggestion = generateSuggestion(results, title, description);

        return RagResponse.builder()
                .similarMatches(matches)
                .resolutionSuggestion(suggestion)
                .aiAvailable(true)
                .build();
    }

    private String generateSuggestion(List<Document> results, String title, String description) {
        if (results.isEmpty()) {
            return "No similar incidents found. This appears to be a new type of incident.";
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
                """.formatted(context, title, description != null ? description : "No description provided");

        return chatModel.call(prompt);
    }

    // ── Circuit breaker fallbacks ──

    public void embedIncidentFallback(Incident incident, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for incident #{}: {}", incident.getId(), ex.getMessage());
    }

    public void embedDocumentFallback(String text, String filename, Exception ex) {
        log.warn("AI service unavailable — skipped embedding for document '{}': {}", filename, ex.getMessage());
    }

    public RagResponse findSimilarFallback(String title, String description, Exception ex) {
        log.warn("AI service unavailable for similarity search: {}", ex.getMessage());
        return RagResponse.builder()
                .similarMatches(List.of())
                .resolutionSuggestion("AI service is currently unavailable. Please try again later.")
                .aiAvailable(false)
                .build();
    }
}
