package com.incidentpilot.service;

import com.incidentpilot.dto.MemberResponse;
import com.incidentpilot.dto.OrganizationResponse;
import com.incidentpilot.exception.ResourceNotFoundException;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.repository.OrganizationRepository;
import com.incidentpilot.repository.UserRepository;
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.security.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only view of the caller's own organization and its members. */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public OrganizationResponse getCurrentOrganization() {
        TenantPrincipal me = CurrentUser.require();
        Organization org = organizationRepository.findById(me.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        return OrganizationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .memberCount(userRepository.countByOrganizationId(org.getId()))
                .inviteCode(me.getRole() == Role.ADMIN ? org.getInviteCode() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers() {
        TenantPrincipal me = CurrentUser.require();
        return userRepository.findByOrganizationIdOrderByUsernameAsc(me.getOrganizationId()).stream()
                .map(MemberResponse::from)
                .toList();
    }
}
