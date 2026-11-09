package com.incidentpilot.repository;

import com.incidentpilot.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Organization> findByInviteCode(String inviteCode);

    Optional<Organization> findByNameIgnoreCase(String name);
}
