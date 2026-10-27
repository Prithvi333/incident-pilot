package com.incidentpilot.controller;

import com.incidentpilot.dto.CreateTimelineEventRequest;
import com.incidentpilot.dto.TimelineEventResponse;
import com.incidentpilot.service.TimelineEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Timeline endpoints are nested under incidents because a timeline event
 * always belongs to a specific incident:
 *   POST /api/incidents/{incidentId}/timeline
 *   GET  /api/incidents/{incidentId}/timeline
 */
@RestController
@RequestMapping("/api/incidents/{incidentId}/timeline")
@RequiredArgsConstructor
public class TimelineEventController {

    private final TimelineEventService timelineEventService;

    @PostMapping
    public ResponseEntity<TimelineEventResponse> addTimelineEvent(
            @PathVariable Long incidentId,
            @Valid @RequestBody CreateTimelineEventRequest request) {
        TimelineEventResponse response = timelineEventService.addEvent(incidentId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TimelineEventResponse>> getTimeline(
            @PathVariable Long incidentId) {
        return ResponseEntity.ok(timelineEventService.getTimeline(incidentId));
    }
}
