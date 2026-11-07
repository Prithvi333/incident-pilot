package com.incidentpilot.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Assignee username; null or blank unassigns the incident. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignIncidentRequest {

    @Size(max = 100, message = "Assignee must not exceed 100 characters")
    private String assignee;
}
