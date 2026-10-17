package com.incidentpilot.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core domain entity — represents a single production incident.
 *
 * Uses @Getter/@Setter instead of @Data because @Data generates equals/hashCode
 * based on all fields, which causes issues with JPA (lazy loading, detached entities).
 */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    /** The person currently responsible for this incident */
    private String assignee;

    /** Which service/system is affected (e.g. "payment-service", "auth-api") */
    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Set when the incident moves to RESOLVED status */
    private LocalDateTime resolvedAt;

    /**
     * One incident has many timeline events.
     * CascadeType.ALL means if we delete an incident, its timeline events go too.
     * orphanRemoval ensures events removed from the list are deleted from the DB.
     * LAZY fetch means events are NOT loaded until you call getTimelineEvents().
     */
    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TimelineEvent> timelineEvents = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = IncidentStatus.TRIGGERED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
