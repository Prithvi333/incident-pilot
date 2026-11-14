package com.incidentpilot.ingestion.service;

import com.incidentpilot.ingestion.dto.DocumentResponse;
import com.incidentpilot.ingestion.exception.DocumentNotFoundException;
import com.incidentpilot.ingestion.exception.InvalidDocumentException;
import com.incidentpilot.ingestion.model.Document;
import com.incidentpilot.ingestion.model.DocumentStatus;
import com.incidentpilot.ingestion.repository.DocumentRepository;
import event.DocumentIngestionEvent;
import event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Knowledge-base pipeline: S3 storage → text extraction → transactional outbox → Kafka → AI service
 * (chunking + embedding in Qdrant).
 *
 * <p>MySQL and Qdrant never share a transaction, so the document row and its outbox event are written
 * together in ONE database transaction; the outbox relay delivers the event (with retries) and moves the
 * document to {@code INDEXED} once Kafka accepted it (or {@code FAILED} if it never can). Deleting a
 * document soft-deletes the row and enqueues a delete event that removes every chunk from Qdrant.
 * Everything is scoped to the caller's organization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".txt", ".md", ".text", ".pdf");

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final OutboxService outboxService;
    private final PlatformTransactionManager transactionManager;

    public DocumentResponse uploadAndIndex(MultipartFile file, Long organizationId) throws IOException {
        validate(file);

        String fileUrl = s3Service.upload(file, organizationId);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String content;
        try {
            content = extractText(file);
        } catch (Exception e) {
            log.error("Failed to extract text from document: {}", file.getOriginalFilename(), e);
            Document failed = tx.execute(s -> documentRepository.save(newDocument(file, fileUrl, organizationId, DocumentStatus.FAILED)));
            return DocumentResponse.from(failed);
        }

        // One transaction: the document row and the event that will embed it
        Document saved = tx.execute(s -> {
            Document doc = documentRepository.save(newDocument(file, fileUrl, organizationId, DocumentStatus.PROCESSING));
            outboxService.enqueue(DocumentIngestionEvent.builder()
                    .eventType(EventType.DOCUMENT)
                    .organizationId(organizationId)
                    .documentId(doc.getId())
                    .text(content)
                    .filename(file.getOriginalFilename())
                    .build());
            return doc;
        });
        return DocumentResponse.from(saved);
    }

    /**
     * Soft-deletes the document and schedules removal of all its chunks from Qdrant. A document of
     * another organization (or an already deleted one) is reported as not found.
     */
    public void deleteDocument(Long id, Long organizationId, String username) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            Document doc = documentRepository.findByIdAndOrganizationId(id, organizationId)
                    .orElseThrow(() -> new DocumentNotFoundException(id));
            doc.setDeletedAt(LocalDateTime.now());
            doc.setDeletedBy(username);
            documentRepository.save(doc);
            outboxService.enqueue(DocumentIngestionEvent.builder()
                    .eventType(EventType.DOCUMENT_DELETE)
                    .organizationId(organizationId)
                    .documentId(doc.getId())
                    .filename(doc.getOriginalFilename())
                    .build());
        });
        log.info("Document #{} (org {}) deleted by {}", id, organizationId, username);
    }

    /** A document id that belongs to another organization is reported as not found. */
    public String getDownloadUrl(Long id, Long organizationId) {
        Document doc = documentRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return s3Service.generatePresignedUrl(doc.getFileUrl());
    }

    public List<DocumentResponse> getAllDocuments(Long organizationId) {
        return documentRepository.findByOrganizationIdOrderByUploadedAtDescIdDesc(organizationId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    private Document newDocument(MultipartFile file, String fileUrl, Long organizationId, DocumentStatus status) {
        return Document.builder()
                .organizationId(organizationId)
                .originalFilename(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .status(status)
                .build();
    }

    /** Server-side check: the frontend restriction alone cannot be trusted. */
    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("File is empty");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new InvalidDocumentException("Only .txt, .md and .pdf files are supported");
        }
    }

    private String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Extracted {} chars from PDF '{}'", text.length(), filename);
                return text;
            }
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
