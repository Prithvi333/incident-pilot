package com.incidentpilot.repository;

import com.incidentpilot.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Retrieves the audit trail for one entity within one organization, most recent first. */
    List<AuditLog> findByEntityTypeAndEntityIdAndOrganizationIdOrderByPerformedAtDesc(
            String entityType, Long entityId, Long organizationId);
}
