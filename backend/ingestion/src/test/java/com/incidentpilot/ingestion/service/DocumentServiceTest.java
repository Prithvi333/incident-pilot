package com.incidentpilot.ingestion.service;

import com.incidentpilot.ingestion.dto.DocumentResponse;
import com.incidentpilot.ingestion.exception.DocumentNotFoundException;
import com.incidentpilot.ingestion.exception.InvalidDocumentException;
import com.incidentpilot.ingestion.kafka.producer.IngestionEventProducer;
import com.incidentpilot.ingestion.model.Document;
import com.incidentpilot.ingestion.model.DocumentStatus;
import com.incidentpilot.ingestion.repository.DocumentRepository;
import event.BaseEvent;
import event.DocumentIngestionEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Organization scoping and validation of the knowledge-base upload pipeline. */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock S3Service s3Service;
    @Mock IngestionEventProducer ingestionEventProducer;
    @InjectMocks DocumentService documentService;

    @Test
    void uploadTagsTheDocumentAndTheKafkaEventWithTheOrganization() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "runbook.md", "text/markdown", "restart the pool".getBytes());
        when(s3Service.upload(file, 5L)).thenReturn("https://bucket/orgs/5/documents/x_runbook.md");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse response = documentService.uploadAndIndex(file, 5L);

        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(5L);
        ArgumentCaptor<BaseEvent> event = ArgumentCaptor.forClass(BaseEvent.class);
        verify(ingestionEventProducer).publish(event.capture());
        assertThat(event.getValue().getOrganizationId()).isEqualTo(5L);
        assertThat(((DocumentIngestionEvent) event.getValue()).getText()).isEqualTo("restart the pool");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.INDEXED);
    }

    @Test
    void unsupportedOrEmptyFilesAreRejectedBeforeAnythingIsStored() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "x".getBytes());
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadAndIndex(exe, 5L)).isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> documentService.uploadAndIndex(empty, 5L)).isInstanceOf(InvalidDocumentException.class);
        verify(s3Service, never()).upload(any(), any());
    }

    @Test
    void failedPublishMarksTheDocumentFailed() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        when(s3Service.upload(file, 5L)).thenReturn("https://bucket/k");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new RuntimeException("kafka down")).when(ingestionEventProducer).publish(any());

        assertThat(documentService.uploadAndIndex(file, 5L).getStatus()).isEqualTo(DocumentStatus.FAILED);
    }

    @Test
    void downloadOfAnotherOrganizationsDocumentIsNotFound() {
        when(documentRepository.findByIdAndOrganizationId(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDownloadUrl(9L, 5L)).isInstanceOf(DocumentNotFoundException.class);
        verify(s3Service, never()).generatePresignedUrl(any());
    }

    @Test
    void listingOnlyReadsTheCallersOrganization() {
        when(documentRepository.findByOrganizationIdOrderByUploadedAtDescIdDesc(5L)).thenReturn(List.of(
                Document.builder().id(1L).organizationId(5L).originalFilename("a.md").fileUrl("u")
                        .status(DocumentStatus.INDEXED).build()));

        assertThat(documentService.getAllDocuments(5L)).hasSize(1);
        verify(documentRepository, never()).findAll();
    }
}
