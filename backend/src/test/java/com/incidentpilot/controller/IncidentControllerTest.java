package com.incidentpilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.UpdateStatusRequest;
import com.incidentpilot.exception.GlobalExceptionHandler;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidTransitionException;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.service.IncidentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the REST API layer.
 * @WebMvcTest loads only the web layer (controller + exception handler),
 * not the full application context — keeps tests fast.
 */
@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentService incidentService;

    @Test
    @DisplayName("POST /api/incidents — creates incident and returns 201")
    void createIncident() throws Exception {
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("API gateway 502 errors")
                .description("50% of requests returning 502")
                .severity(Severity.P1_CRITICAL)
                .assignee("on-call")
                .serviceName("api-gateway")
                .build();

        IncidentResponse response = IncidentResponse.builder()
                .id(1L)
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .status(IncidentStatus.TRIGGERED)
                .assignee(request.getAssignee())
                .serviceName(request.getServiceName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(incidentService.createIncident(any(CreateIncidentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("API gateway 502 errors"))
                .andExpect(jsonPath("$.status").value("TRIGGERED"));
    }

    @Test
    @DisplayName("POST /api/incidents — blank title returns 400 with validation errors")
    void createIncidentValidationFails() throws Exception {
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("")
                .serviceName("")
                .build();

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("GET /api/incidents — returns list of incidents")
    void getAllIncidents() throws Exception {
        List<IncidentResponse> responses = List.of(
                IncidentResponse.builder().id(1L).title("Incident 1")
                        .status(IncidentStatus.TRIGGERED).severity(Severity.P2_HIGH)
                        .serviceName("svc-a").createdAt(LocalDateTime.now()).build(),
                IncidentResponse.builder().id(2L).title("Incident 2")
                        .status(IncidentStatus.INVESTIGATING).severity(Severity.P3_MEDIUM)
                        .serviceName("svc-b").createdAt(LocalDateTime.now()).build()
        );
        when(incidentService.getAllIncidents()).thenReturn(responses);

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/incidents/{id} — returns 404 for unknown ID")
    void getIncidentNotFound() throws Exception {
        when(incidentService.getIncident(99L)).thenThrow(new IncidentNotFoundException(99L));

        mockMvc.perform(get("/api/incidents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Incident not found with id: 99"));
    }

    @Test
    @DisplayName("PATCH /api/incidents/{id}/status — valid transition returns 200")
    void updateStatusValid() throws Exception {
        UpdateStatusRequest request = UpdateStatusRequest.builder()
                .status(IncidentStatus.ACKNOWLEDGED)
                .build();

        IncidentResponse response = IncidentResponse.builder()
                .id(1L).title("Test").status(IncidentStatus.ACKNOWLEDGED)
                .severity(Severity.P2_HIGH).serviceName("svc")
                .createdAt(LocalDateTime.now()).build();

        when(incidentService.updateStatus(eq(1L), eq(IncidentStatus.ACKNOWLEDGED))).thenReturn(response);

        mockMvc.perform(patch("/api/incidents/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("PATCH /api/incidents/{id}/status — invalid transition returns 400")
    void updateStatusInvalid() throws Exception {
        UpdateStatusRequest request = UpdateStatusRequest.builder()
                .status(IncidentStatus.RESOLVED)
                .build();

        when(incidentService.updateStatus(eq(1L), eq(IncidentStatus.RESOLVED)))
                .thenThrow(new InvalidTransitionException(IncidentStatus.TRIGGERED, IncidentStatus.RESOLVED));

        mockMvc.perform(patch("/api/incidents/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot transition from TRIGGERED to RESOLVED"));
    }
}
