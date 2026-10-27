package com.incidentpilot.ingestion.controller;

import com.incidentpilot.ingestion.dto.DocumentResponse;
import com.incidentpilot.ingestion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base API — upload postmortems and runbooks for AI-powered resolution suggestions.
 *
 * <p>Uploaded files are stored in S3, then their text content is embedded into the
 * Qdrant vector store so the RAG pipeline can retrieve relevant context when new
 * incidents occur. Only ADMINs can upload; all authenticated users can list.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        DocumentResponse response = documentService.uploadAndIndex(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, String>> downloadDocument(@PathVariable Long id) {
        String presignedUrl = documentService.getDownloadUrl(id);
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }
}

