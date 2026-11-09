package com.incidentpilot.ingestion.service;

import com.incidentpilot.ingestion.dto.DocumentResponse;
import com.incidentpilot.ingestion.exception.DocumentNotFoundException;
import com.incidentpilot.ingestion.exception.InvalidDocumentException;
import com.incidentpilot.ingestion.kafka.producer.IngestionEventProducer;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the document upload pipeline: S3 storage → text extraction → vector embedding.
 *
 * <p>The pipeline is: upload file to S3 for durable storage, then extract its text content
 * and publish it to Kafka so the AI service embeds it into the vector store for RAG retrieval.
 * Every document belongs to the uploader's organization: listing and downloading are scoped to it,
 * and the Kafka event carries the organization so the vector is tagged for tenant-isolated search.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".txt", ".md", ".text", ".pdf");

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final IngestionEventProducer ingestionEventProducer;

    public DocumentResponse uploadAndIndex(MultipartFile file, Long organizationId) throws IOException {
        validate(file);

        String fileUrl = s3Service.upload(file, organizationId);

        Document doc = Document.builder()
                .organizationId(organizationId)
                .originalFilename(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .status(DocumentStatus.PROCESSING)
                .build();
        doc = documentRepository.save(doc);

        try {
            String content = extractText(file);
            ingestionEventProducer.publish(DocumentIngestionEvent.builder()
                    .organizationId(organizationId)
                    .eventType(EventType.DOCUMENT)
                    .text(content)
                    .filename(file.getOriginalFilename())
                    .build());
            doc.setStatus(DocumentStatus.INDEXED);
            doc.setIndexedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to index document: {}", file.getOriginalFilename(), e);
            doc.setStatus(DocumentStatus.FAILED);
        }

        doc = documentRepository.save(doc);
        return DocumentResponse.from(doc);
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
