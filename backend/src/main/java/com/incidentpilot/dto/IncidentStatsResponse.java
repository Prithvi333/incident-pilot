package com.incidentpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentStatsResponse {

    private long totalIncidents;
    private long activeIncidents;
    private long resolvedIncidents;

    /** Average time to resolve in minutes (null if no resolved incidents yet) */
    private Double averageResolutionMinutes;

    /** Breakdown: e.g. {"P1_CRITICAL": 2, "P2_HIGH": 5} */
    private Map<String, Long> countBySeverity;

    /** Breakdown: e.g. {"TRIGGERED": 3, "INVESTIGATING": 1} */
    private Map<String, Long> countByStatus;
}
