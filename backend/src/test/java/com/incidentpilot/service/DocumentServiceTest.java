package com.incidentpilot.service;

import com.incidentpilot.dto.DocumentResponse;
import com.incidentpilot.model.Document;
import com.incidentpilot.model.DocumentStatus;
import com.incidentpilot.repository.DocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private RagService ragService;

    @InjectMocks
    private DocumentService documentService;

    @Test
    @DisplayName("uploadAndIndex uploads to S3, saves document, and indexes successfully")
    void uploadAndIndexSuccess() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "postmortem.txt", "text/plain", "Incident report content".getBytes());

        when(s3Service.upload(file)).thenReturn("https://s3.amazonaws.com/bucket/documents/uuid_postmortem.txt");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(1L);
            return doc;
        });

        DocumentResponse response = documentService.uploadAndIndex(file);

        assertThat(response.getOriginalFilename()).isEqualTo("postmortem.txt");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(response.getFileUrl()).contains("s3.amazonaws.com");

        verify(s3Service).upload(file);
        verify(ragService).embedDocument(eq("Incident report content"), eq("postmortem.txt"));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        Document finalSave = captor.getAllValues().get(1);
        assertThat(finalSave.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(finalSave.getIndexedAt()).isNotNull();
    }

    @Test
    @DisplayName("uploadAndIndex sets FAILED status when RAG indexing throws")
    void uploadAndIndexFailure() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "runbook.md", "text/plain", "Steps to fix".getBytes());

        when(s3Service.upload(file)).thenReturn("https://s3.amazonaws.com/bucket/documents/uuid_runbook.md");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document doc = inv.getArgument(0);
            doc.setId(2L);
            return doc;
        });
        doThrow(new RuntimeException("Qdrant unavailable"))
                .when(ragService).embedDocument(anyString(), anyString());

        DocumentResponse response = documentService.uploadAndIndex(file);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(response.getIndexedAt()).isNull();
    }

    @Test
    @DisplayName("uploadAndIndex propagates IOException from S3")
    void uploadThrowsIOException() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "content".getBytes());

        when(s3Service.upload(file)).thenThrow(new IOException("S3 connection failed"));

        assertThatThrownBy(() -> documentService.uploadAndIndex(file))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("S3 connection failed");

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("getAllDocuments returns mapped DTOs")
    void getAllDocuments() {
        Document doc1 = Document.builder()
                .id(1L).originalFilename("doc1.txt").fileUrl("url1")
                .contentType("text/plain").status(DocumentStatus.INDEXED).build();
        Document doc2 = Document.builder()
                .id(2L).originalFilename("doc2.md").fileUrl("url2")
                .contentType("text/markdown").status(DocumentStatus.FAILED).build();

        when(documentRepository.findAll()).thenReturn(List.of(doc1, doc2));

        List<DocumentResponse> results = documentService.getAllDocuments();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOriginalFilename()).isEqualTo("doc1.txt");
        assertThat(results.get(0).getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(results.get(1).getStatus()).isEqualTo(DocumentStatus.FAILED);
    }
}
