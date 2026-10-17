package com.incidentpilot.dto;

import com.incidentpilot.model.TimelineEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTimelineEventRequest {

    @NotNull(message = "Event type is required")
    private TimelineEventType type;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Author is required")
    private String author;
}
