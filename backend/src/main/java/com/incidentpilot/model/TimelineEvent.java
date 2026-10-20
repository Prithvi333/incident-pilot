package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An append-only event in an incident's timeline.
 *
 * Think of it like a chat log for an outage — every status change, comment,
 * and action is recorded here in order. Events are never edited or deleted,
 * which gives you a reliable audit trail and raw material for postmortems.
 */
@Entity
@Table(name = "timeline_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Many timeline events belong to one incident.
     * LAZY fetch means events are only loaded when you explicitly ask for them,
     * not every time you load an incident — this avoids N+1 query problems.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimelineEventType type;

    /** Human-readable description of what happened */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Who created this entry (engineer name or "system" for auto-generated) */
    @Column(nullable = false)
    private String author;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
