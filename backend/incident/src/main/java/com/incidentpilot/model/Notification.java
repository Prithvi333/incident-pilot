package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A persisted, per-user notification about incident activity.
 *
 * <p>{@code dedupeKey} identifies the logical event (type + incident + version/comment id), and the
 * unique constraint on (recipient, dedupeKey) guarantees a user is never notified twice for it,
 * even if the same event is processed again.
 */
@Entity
@Table(name = "notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_recipient_dedupe",
                columnNames = {"recipientId", "dedupeKey"}),
        indexes = @Index(name = "idx_notification_recipient_read", columnList = "recipientId, organizationId, is_read"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private Long recipientId;

    private Long incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    /** Username of whoever caused the activity ("system" for SLA alerts). */
    @Column(length = 100)
    private String actor;

    @Column(nullable = false, length = 191)
    private String dedupeKey;

    /** Column is named is_read because READ is a reserved word in MySQL. */
    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
