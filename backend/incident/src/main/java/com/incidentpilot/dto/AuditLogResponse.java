package com.incidentpilot.dto;

import com.incidentpilot.model.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String action;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private String performedBy;
    private LocalDateTime performedAt;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .performedBy(log.getPerformedBy())
                .performedAt(log.getPerformedAt())
                .build();
    }
}
