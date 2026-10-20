package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Knowledge base document — a postmortem or runbook uploaded by an admin.
 *
 * <p>Lifecycle tracked by {@link DocumentStatus}:
 * UPLOADED → PROCESSING (embedding) → INDEXED (searchable) or FAILED.
 * The file itself lives in S3; this entity stores metadata and indexing state.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String fileUrl;

    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    private LocalDateTime indexedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = DocumentStatus.UPLOADED;
        }
    }
}
