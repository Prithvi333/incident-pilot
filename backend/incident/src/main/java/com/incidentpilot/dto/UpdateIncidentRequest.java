package com.incidentpilot.dto;

import com.incidentpilot.model.Severity;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial update: only non-null fields are applied. Status and assignee have their own endpoints. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIncidentRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Pattern(regexp = ".*\\S.*", message = "Title must not be blank")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private Severity severity;

    @Size(max = 100, message = "Service name must not exceed 100 characters")
    @Pattern(regexp = ".*\\S.*", message = "Service name must not be blank")
    private String serviceName;
}
