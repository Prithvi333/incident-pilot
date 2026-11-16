package com.incidentpilot.ingestion.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Transactional outbox row for the knowledge base: "embed this document" / "delete this document's
 * vectors". Written in the same transaction as the document row, delivered to Kafka by
 * {@code OutboxRelay} with retries, so MySQL and Qdrant converge even if Kafka or Qdrant are briefly down.
 */
@Entity
@Table(name = "ingestion_outbox",
        indexes = @Index(name = "idx_ingestion_outbox_status_id", columnList = "status, id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizationId;

    private Long documentId;

    /** {@code event.EventType} name: DOCUMENT (embed) or DOCUMENT_DELETE (remove all chunks). */
    @Column(nullable = false, length = 40)
    private String eventType;

    /** Kafka key / ordering scope: all events of one document stay in order on one partition. */
    @Column(nullable = false, length = 100)
    private String aggregateKey;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @Column(length = 500)
    private String lastError;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = Status.PENDING;
        }
        if (this.nextAttemptAt == null) {
            this.nextAttemptAt = this.createdAt;
        }
    }
}
