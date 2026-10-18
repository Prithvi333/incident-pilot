package com.incidentpilot.controller;

import com.incidentpilot.dto.DocumentResponse;
import com.incidentpilot.model.DocumentStatus;
import com.incidentpilot.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@WithMockUser(roles = "ADMIN")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private com.incidentpilot.security.JwtService jwtService;

    @MockitoBean
    private com.incidentpilot.security.CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/documents — uploads document and returns 201")
    void uploadDocument() throws Exception {
        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .originalFilename("postmortem.txt")
                .fileUrl("https://incident-pilot-docs.s3.amazonaws.com/documents/abc_postmortem.txt")
                .contentType("text/plain")
                .status(DocumentStatus.INDEXED)
                .uploadedAt(LocalDateTime.now())
                .indexedAt(LocalDateTime.now())
                .build();

        when(documentService.uploadAndIndex(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "postmortem.txt", "text/plain", "Payment service outage root cause".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("postmortem.txt"))
                .andExpect(jsonPath("$.fileUrl").value("https://incident-pilot-docs.s3.amazonaws.com/documents/abc_postmortem.txt"))
                .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    @DisplayName("POST /api/documents — returns 201 when indexing fails")
    void uploadDocumentIndexingFailed() throws Exception {
        DocumentResponse response = DocumentResponse.builder()
                .id(2L)
                .originalFilename("bad-doc.txt")
                .contentType("text/plain")
                .status(DocumentStatus.FAILED)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(documentService.uploadAndIndex(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad-doc.txt", "text/plain", "some content".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    @DisplayName("GET /api/documents — returns list of documents")
    void getAllDocuments() throws Exception {
        DocumentResponse doc = DocumentResponse.builder()
                .id(1L)
                .originalFilename("postmortem.txt")
                .contentType("text/plain")
                .status(DocumentStatus.INDEXED)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(documentService.getAllDocuments()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("postmortem.txt"));
    }

}
