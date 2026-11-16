package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Transactional outbox row: an event that must reach Kafka (and therefore Qdrant) because an
 * incident changed. It is written in the SAME database transaction as the incident change, so
 * MySQL and the event can never disagree; {@code OutboxRelay} publishes it afterwards and retries
 * with backoff until Kafka acknowledges it.
 */
@Entity
@Table(name = "incident_outbox",
        indexes = @Index(name = "idx_incident_outbox_status_id", columnList = "status, id"))
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

    /** {@code event.EventType} name (INCIDENT = upsert embedding, INCIDENT_DELETE = remove it). */
    @Column(nullable = false, length = 40)
    private String eventType;

    /** Kafka key and ordering scope: events of one incident always go to one partition, in order. */
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
