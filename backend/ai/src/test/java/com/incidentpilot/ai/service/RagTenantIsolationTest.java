package com.incidentpilot.ai.service;

import com.incidentpilot.ai.dto.RagResponse;
import event.EventType;
import event.IncidentDocumentIngestionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RAG tenant isolation (search filter, vector tagging) and idempotent embedding. */
class RagTenantIsolationTest {

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private IncidentDocumentIngestionEvent incidentEvent(Long orgId, Long incidentId) {
        return IncidentDocumentIngestionEvent.builder()
                .eventType(EventType.INCIDENT).organizationId(orgId).id(incidentId)
                .title("DB down").serviceName("db").severityName("P1_CRITICAL").description("timeouts")
                .build();
    }

    @Test
    void similaritySearchIsAlwaysFilteredByTheCallersOrganization() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(vectorStore.similaritySearch(org.mockito.ArgumentMatchers.any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document("1", "Incident: same", Map.of("source", "incident:5")),
                        new Document("2", "Incident: other", Map.of("source", "incident:6"))));
        when(chatModel.call(anyString())).thenReturn("restart the pool");
        IncidentAiService service = new IncidentAiService(provider(vectorStore), provider(chatModel));

        RagResponse response = service.findSimilarIncidents(7L, 5L, "DB down", "timeouts");

        ArgumentCaptor<SearchRequest> request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getFilterExpression()).isNotNull();
        assertThat(request.getValue().getFilterExpression().toString())
                .contains("organizationId").contains("7");
        // the incident itself is excluded from its own recommendations
        assertThat(response.getSimilarMatches()).extracting(m -> m.getSource()).containsExactly("incident:6");
        assertThat(response.getResolutionSuggestion()).isEqualTo("restart the pool");
        assertThat(response.isAiAvailable()).isTrue();
    }

    @Test
    void searchDegradesGracefullyWithoutVectorStoreOrLlm() {
        IncidentAiService service = new IncidentAiService(provider(null), provider(null));

        RagResponse response = service.findSimilarIncidents(7L, 5L, "DB down", null);

        assertThat(response.getSimilarMatches()).isEmpty();
        assertThat(response.isAiAvailable()).isFalse();
    }

    @Test
    void embeddedIncidentIsTaggedWithItsOrganizationAndRealSeverity() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagService ragService = new RagService(provider(vectorStore), provider(mock(ChatModel.class)));

        ragService.embedIncident(incidentEvent(3L, 5L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(docs.capture());
        Document doc = docs.getValue().get(0);
        assertThat(doc.getMetadata()).containsEntry("organizationId", "3").containsEntry("source", "incident:5");
        assertThat(doc.getText()).contains("Severity: P1_CRITICAL").doesNotContain("Severity: db");
    }

    @Test
    void redeliveredEventUpdatesTheSameVectorInsteadOfDuplicatingIt() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagService ragService = new RagService(provider(vectorStore), provider(mock(ChatModel.class)));

        ragService.embedIncident(incidentEvent(3L, 5L));
        ragService.embedIncident(incidentEvent(3L, 5L));
        ragService.embedIncident(incidentEvent(4L, 5L)); // same incident number, different tenant

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(docs.capture());
        List<String> ids = docs.getAllValues().stream().map(l -> l.get(0).getId()).toList();
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(ids.get(2)).isNotEqualTo(ids.get(0));
    }

    @Test
    void eventsWithoutOrganizationAreIgnored() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagService ragService = new RagService(provider(vectorStore), provider(mock(ChatModel.class)));

        ragService.embedIncident(incidentEvent(null, 5L));
        ragService.embedDocument("text", "runbook.md", null, "evt-1");

        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void embeddedDocumentIsTaggedAndIdempotent() {
        VectorStore vectorStore = mock(VectorStore.class);
        RagService ragService = new RagService(provider(vectorStore), provider(mock(ChatModel.class)));

        ragService.embedDocument("text", "runbook.md", 3L, "evt-1");
        ragService.embedDocument("text", "runbook.md", 3L, "evt-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> docs = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(docs.capture());
        Document first = docs.getAllValues().get(0).get(0);
        assertThat(first.getMetadata()).containsEntry("organizationId", "3").containsEntry("source", "runbook.md");
        assertThat(first.getId()).isEqualTo(docs.getAllValues().get(1).get(0).getId());
    }
}
