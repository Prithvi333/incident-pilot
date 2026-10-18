package com.incidentpilot.service;

import com.incidentpilot.dto.RagResponse;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private RagService ragService;

    @Test
    @DisplayName("embedIncident stores incident in vector store")
    void embedIncident() {
        Incident incident = Incident.builder()
                .id(1L)
                .title("Payment gateway timeout")
                .description("Stripe API returning 504s")
                .serviceName("payment-service")
                .severity(Severity.P1_CRITICAL)
                .status(IncidentStatus.TRIGGERED)
                .build();

        ragService.embedIncident(incident);

        verify(vectorStore).add(any(List.class));
    }

    @Test
    @DisplayName("embedDocument stores document text in vector store")
    void embedDocument() {
        ragService.embedDocument("Postmortem: DB connection pool exhaustion", "postmortem-2024-01.txt");

        verify(vectorStore).add(any(List.class));
    }

    @Test
    @DisplayName("findSimilarIncidents returns matches and AI suggestion")
    void findSimilarIncidents() {
        Document doc = new Document("Similar incident: payment timeout resolved by increasing pool size",
                Map.of("source", "incident:5", "type", "incident"));

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(chatModel.call(anyString())).thenReturn("Increase connection pool size and add retry logic.");

        RagResponse response = ragService.findSimilarIncidents("Payment timeout", "Stripe returns 504");

        assertThat(response.isAiAvailable()).isTrue();
        assertThat(response.getSimilarMatches()).hasSize(1);
        assertThat(response.getSimilarMatches().get(0).getSource()).isEqualTo("incident:5");
        assertThat(response.getResolutionSuggestion()).contains("pool size");
    }

    @Test
    @DisplayName("findSimilarIncidents returns no-match message when vector store is empty")
    void findSimilarIncidentsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RagResponse response = ragService.findSimilarIncidents("Brand new issue", null);

        assertThat(response.isAiAvailable()).isTrue();
        assertThat(response.getSimilarMatches()).isEmpty();
        assertThat(response.getResolutionSuggestion()).contains("new type of incident");
    }

    @Test
    @DisplayName("Fallback returns unavailable response")
    void findSimilarFallback() {
        RagResponse response = ragService.findSimilarFallback("title", "desc", new RuntimeException("AI down"));

        assertThat(response.isAiAvailable()).isFalse();
        assertThat(response.getSimilarMatches()).isEmpty();
        assertThat(response.getResolutionSuggestion()).contains("unavailable");
    }
}
