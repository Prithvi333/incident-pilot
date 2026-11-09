package com.incidentpilot.repository;

import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Loads the organization together with the user (needed for tokens and the principal). */
    @EntityGraph(attributePaths = "organization")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = "organization")
    List<User> findByOrganizationIdOrderByUsernameAsc(Long organizationId);

    @EntityGraph(attributePaths = "organization")
    List<User> findByOrganizationIdAndRole(Long organizationId, Role role);

    @EntityGraph(attributePaths = "organization")
    Optional<User> findByOrganizationIdAndUsername(Long organizationId, String username);

    @EntityGraph(attributePaths = "organization")
    List<User> findByOrganizationIdAndUsernameIn(Long organizationId, Collection<String> usernames);

    long countByOrganizationId(Long organizationId);
}
