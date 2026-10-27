package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record for tracking who changed what and when.
 *
 * <p>Used for incident lifecycle events (CREATE, STATUS_CHANGE) and SLA breach detection.
 * Records are append-only — never updated or deleted — to maintain a reliable audit trail.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    private String oldValue;

    private String newValue;

    @Column(nullable = false)
    private String performedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }
}
