package com.incidentpilot.service;

import com.incidentpilot.dto.DocumentResponse;
import com.incidentpilot.model.Document;
import com.incidentpilot.model.DocumentStatus;
import com.incidentpilot.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

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
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
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

    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(DocumentResponse::from)
                .toList();
    }
}
