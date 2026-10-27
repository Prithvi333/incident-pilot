package com.incidentpilot.ai.controller;

import com.incidentpilot.ai.dto.RagResponse;
import com.incidentpilot.ai.service.IncidentAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/incidents")
@RequiredArgsConstructor
public class IncidentAiController {

    private final IncidentAiService incidentAiService;

    @GetMapping("/{incidentId}/similar")
    public ResponseEntity<RagResponse> findSimilarIncidents(
            @PathVariable Long incidentId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {

        RagResponse response = incidentAiService.findSimilarIncidents(
                incidentId,
                title,
                description
        );

        return ResponseEntity.ok(response);
    }
}