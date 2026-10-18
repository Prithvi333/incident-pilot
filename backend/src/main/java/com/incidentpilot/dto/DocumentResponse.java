package com.incidentpilot.dto;

import com.incidentpilot.model.Document;
import com.incidentpilot.model.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String originalFilename;
    private String fileUrl;
    private String contentType;
    private DocumentStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime indexedAt;

    public static DocumentResponse from(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .fileUrl(doc.getFileUrl())
                .contentType(doc.getContentType())
                .status(doc.getStatus())
                .uploadedAt(doc.getUploadedAt())
                .indexedAt(doc.getIndexedAt())
                .build();
    }
}
