package com.incidentpilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.dto.CreateTimelineEventRequest;
import com.incidentpilot.dto.TimelineEventResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.model.TimelineEventType;
import com.incidentpilot.service.TimelineEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimelineEventController.class)
class TimelineEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TimelineEventService timelineEventService;

    @Test
    @DisplayName("POST /api/incidents/{id}/timeline — adds event and returns 201")
    void addTimelineEvent() throws Exception {
        CreateTimelineEventRequest request = CreateTimelineEventRequest.builder()
                .type(TimelineEventType.COMMENT)
                .message("DB connection pool at 100%")
                .author("on-call-eng")
                .build();

        TimelineEventResponse response = TimelineEventResponse.builder()
                .id(1L)
                .incidentId(1L)
                .type(TimelineEventType.COMMENT)
                .message("DB connection pool at 100%")
                .author("on-call-eng")
                .createdAt(LocalDateTime.now())
                .build();

        when(timelineEventService.addEvent(eq(1L), any(CreateTimelineEventRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/incidents/1/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("DB connection pool at 100%"))
                .andExpect(jsonPath("$.type").value("COMMENT"))
                .andExpect(jsonPath("$.author").value("on-call-eng"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/timeline — blank message returns 400")
    void addTimelineEventValidationFails() throws Exception {
        CreateTimelineEventRequest request = CreateTimelineEventRequest.builder()
                .type(TimelineEventType.COMMENT)
                .message("")
                .author("")
                .build();

        mockMvc.perform(post("/api/incidents/1/timeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/incidents/{id}/timeline — returns timeline events")
    void getTimeline() throws Exception {
        List<TimelineEventResponse> timeline = List.of(
                TimelineEventResponse.builder().id(1L).incidentId(1L)
                        .type(TimelineEventType.STATUS_CHANGE)
                        .message("Status changed from TRIGGERED to ACKNOWLEDGED")
                        .author("system").createdAt(LocalDateTime.now().minusMinutes(5)).build(),
                TimelineEventResponse.builder().id(2L).incidentId(1L)
                        .type(TimelineEventType.COMMENT)
                        .message("Investigating DB")
                        .author("eng").createdAt(LocalDateTime.now()).build()
        );
        when(timelineEventService.getTimeline(1L)).thenReturn(timeline);

        mockMvc.perform(get("/api/incidents/1/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$[1].type").value("COMMENT"));
    }

    @Test
    @DisplayName("GET /api/incidents/{id}/timeline — 404 for unknown incident")
    void getTimelineNotFound() throws Exception {
        when(timelineEventService.getTimeline(99L))
                .thenThrow(new IncidentNotFoundException(99L));

        mockMvc.perform(get("/api/incidents/99/timeline"))
                .andExpect(status().isNotFound());
    }
}
