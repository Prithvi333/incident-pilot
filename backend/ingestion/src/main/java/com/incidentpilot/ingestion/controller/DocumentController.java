package com.incidentpilot.ingestion.controller;

import com.incidentpilot.ingestion.dto.DocumentResponse;
import com.incidentpilot.ingestion.security.AuthenticatedUser;
import com.incidentpilot.ingestion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base API — upload postmortems and runbooks for AI-powered resolution suggestions.
 *
 * <p>Uploaded files are stored in S3, then their text is published to Kafka so the AI service can
 * embed it into the Qdrant vector store for RAG retrieval. ENGINEERs and ADMINs can upload; all
 * authenticated users can list and download. Everything is scoped to the caller's organization,
 * taken from the verified JWT.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file) throws IOException {
        DocumentResponse response = documentService.uploadAndIndex(file, user.organizationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, String>> downloadDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        String presignedUrl = documentService.getDownloadUrl(id, user.organizationId());
        return ResponseEntity.ok(Map.of("url", presignedUrl));
    }

    /** Soft delete (ENGINEER/ADMIN): removes the document from listings and all its chunks from the vector store. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        documentService.deleteDocument(id, user.organizationId(), user.username());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAllDocuments(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(documentService.getAllDocuments(user.organizationId()));
    }
}
