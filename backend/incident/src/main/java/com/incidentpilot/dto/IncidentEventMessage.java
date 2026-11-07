package com.incidentpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Payload broadcast to {@code /topic/org.{organizationId}.incidents}.
 *
 * <p>{@code eventId} is unique per event so clients can drop duplicates (e.g. after a reconnect).
 * {@code incident} is a snapshot taken at commit time; {@code data} carries event-specific details
 * (previous/new values, comment text, SLA thresholds).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEventMessage {
    private String eventId;
    private IncidentEventType type;
    private Long incidentId;
    private Long organizationId;
    private String actor;
    private LocalDateTime occurredAt;
    private IncidentResponse incident;
    private Map<String, Object> data;
}
