package com.incidentpilot.repository;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every tenant-facing query takes an organization id. There is deliberately no unscoped
 * "find by id" used by controllers/services: a foreign incident id resolves to "not found".
 */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByIdAndOrganizationId(Long id, Long organizationId);

    List<Incident> findByOrganizationId(Long organizationId);

    /** Used only by the system-level SLA scheduler, which works across all organizations. */
    @Query("SELECT i.id FROM Incident i WHERE i.status IN :statuses AND i.organization IS NOT NULL")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<IncidentStatus> statuses);

    @Query("SELECT i FROM Incident i WHERE i.organization.id = :orgId "
            + "AND (:status IS NULL OR i.status = :status) "
            + "AND (:severity IS NULL OR i.severity = :severity) "
            + "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.serviceName) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "ORDER BY i.createdAt DESC, i.id DESC")
    List<Incident> searchIncidents(@Param("orgId") Long organizationId,
                                   @Param("status") IncidentStatus status,
                                   @Param("severity") Severity severity,
                                   @Param("search") String search);
}
