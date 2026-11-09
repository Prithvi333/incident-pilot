package com.incidentpilot.controller;

import com.incidentpilot.dto.MemberResponse;
import com.incidentpilot.dto.OrganizationResponse;
import com.incidentpilot.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The caller's own organization and its members (used e.g. for the assignee picker). */
@RestController
@RequestMapping("/api/organization")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<OrganizationResponse> getOrganization() {
        return ResponseEntity.ok(organizationService.getCurrentOrganization());
    }

    @GetMapping("/members")
    public ResponseEntity<List<MemberResponse>> getMembers() {
        return ResponseEntity.ok(organizationService.getMembers());
    }
}
