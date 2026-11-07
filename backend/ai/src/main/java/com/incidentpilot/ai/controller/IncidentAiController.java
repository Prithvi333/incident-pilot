package com.incidentpilot.ai.controller;

import com.incidentpilot.ai.dto.RagResponse;
import com.incidentpilot.ai.security.AuthenticatedUser;
import com.incidentpilot.ai.service.IncidentAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/incidents")
@RequiredArgsConstructor
public class IncidentAiController {

    private final IncidentAiService incidentAiService;

    /**
     * The organization used to filter the vector search comes from the verified JWT,
     * never from a request parameter, so a caller cannot search another tenant's knowledge.
     */
    @GetMapping("/{incidentId}/similar")
    public ResponseEntity<RagResponse> findSimilarIncidents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long incidentId,
            @RequestParam String title,
            @RequestParam(required = false) String description) {

        RagResponse response = incidentAiService.findSimilarIncidents(
                user.organizationId(),
                incidentId,
                title,
                description
        );

        return ResponseEntity.ok(response);
    }
}
