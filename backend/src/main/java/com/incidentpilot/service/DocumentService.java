package com.incidentpilot.service;

import com.incidentpilot.dto.DocumentResponse;
import com.incidentpilot.model.Document;
import com.incidentpilot.model.DocumentStatus;
import com.incidentpilot.repository.DocumentRepository;
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

/**
 * Orchestrates the document upload pipeline: S3 storage → text extraction → vector embedding.
 *
 * <p>The pipeline is: upload file to S3 for durable storage, then extract its text content
 * and embed it into the Qdrant vector store so the RAG service can retrieve it during
 * incident similarity search. If embedding fails, the file is still in S3 — status tracks
 * the indexing state so the user knows whether it's searchable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final RagService ragService;

    public DocumentResponse uploadAndIndex(MultipartFile file) throws IOException {
        String fileUrl = s3Service.upload(file);

        Document doc = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .fileUrl(fileUrl)
                .contentType(file.getContentType())
                .status(DocumentStatus.PROCESSING)
                .build();
        doc = documentRepository.save(doc);

        try {
            String content = extractText(file);
            ragService.embedDocument(content, file.getOriginalFilename());
            doc.setStatus(DocumentStatus.INDEXED);
            doc.setIndexedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to index document: {}", file.getOriginalFilename(), e);
            doc.setStatus(DocumentStatus.FAILED);
        }

        doc = documentRepository.save(doc);
        return DocumentResponse.from(doc);
    }

    public String getDownloadUrl(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
        return s3Service.generatePresignedUrl(doc.getFileUrl());
    }

    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
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
