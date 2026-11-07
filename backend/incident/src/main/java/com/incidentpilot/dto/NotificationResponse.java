package com.incidentpilot.dto;

import com.incidentpilot.model.Notification;
import com.incidentpilot.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private Long incidentId;
    private String actor;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .incidentId(n.getIncidentId())
                .actor(n.getActor())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
