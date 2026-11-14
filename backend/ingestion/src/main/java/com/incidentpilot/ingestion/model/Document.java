package com.incidentpilot.ingestion.model;


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

    /**
     * Owning organization. Nullable at the DB level only for documents uploaded before
     * multi-tenancy (backfilled by the incident service's startup migration); always set for new uploads.
     */
    @Column(name = "organization_id")
    private Long organizationId;

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

    /** Soft delete: set when the document is deleted; its vectors are removed asynchronously. */
    private LocalDateTime deletedAt;

    @Column(length = 100)
    private String deletedBy;

    /** Optimistic lock: the upload pipeline, outbox relay and delete may touch the same row. */
    @Version
    @Column(columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private long version;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = DocumentStatus.UPLOADED;
        }
    }
}

