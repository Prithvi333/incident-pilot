package com.incidentpilot.repository;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository — method names are parsed into SQL queries automatically.
 * For complex queries, upgrade to {@code @Query} or JPA Specifications.
 */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findBySeverity(Severity severity);

    List<Incident> findByStatusAndSeverity(IncidentStatus status, Severity severity);

    List<Incident> findByServiceName(String serviceName);

    /** Used by SlaService to find all open incidents that need SLA checking. */
    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    @Query("SELECT i FROM Incident i WHERE "
            + "(:status IS NULL OR i.status = :status) "
            + "AND (:severity IS NULL OR i.severity = :severity) "
            + "AND (:search IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(i.serviceName) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Incident> searchIncidents(@Param("status") IncidentStatus status,
                                   @Param("severity") Severity severity,
                                   @Param("search") String search);
}
