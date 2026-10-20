package com.incidentpilot.dto;

import com.incidentpilot.model.TimelineEvent;
import com.incidentpilot.model.TimelineEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineEventResponse {

    private Long id;
    private Long incidentId;
    private TimelineEventType type;
    private String message;
    private String author;
    private LocalDateTime createdAt;

    public static TimelineEventResponse from(TimelineEvent event) {
        return TimelineEventResponse.builder()
                .id(event.getId())
                .incidentId(event.getIncident().getId())
                .type(event.getType())
                .message(event.getMessage())
                .author(event.getAuthor())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
