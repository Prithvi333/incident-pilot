package com.incidentpilot.dto;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A past incident that resembles another one, found from the database alone (no AI service needed). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedIncidentResponse {
    private Long id;
    private String title;
    private Severity severity;
    private IncidentStatus status;
    private String serviceName;
    private double score;
    private LocalDateTime createdAt;

    public static RelatedIncidentResponse from(Incident incident, double score) {
        return RelatedIncidentResponse.builder()
                .id(incident.getId())
                .title(incident.getTitle())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .serviceName(incident.getServiceName())
                .score(score)
                .createdAt(incident.getCreatedAt())
                .build();
    }
}
