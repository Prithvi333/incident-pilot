package com.incidentpilot.dto;

import com.incidentpilot.model.TimelineEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;

    /**
     * Deprecated and ignored: the author is always the authenticated user. Kept as an optional
     * field so older clients that still send it keep working.
     */
    @Size(max = 100, message = "Author must not exceed 100 characters")
    private String author;
}
