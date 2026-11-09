package com.incidentpilot.service;

import com.incidentpilot.model.AuditLog;
import com.incidentpilot.repository.AuditLogRepository;
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.security.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Immutable audit trail for incident lifecycle events (creates, status changes, SLA breaches).
 *
 * <p>Records who did what, when, and what changed. The current user is resolved from
 * the Spring Security context; background jobs (SLA checks) log as "system". Every entry is tagged
 * with the organization of the audited entity, and reads are always filtered by it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Writes an audit entry in a NEW transaction so the log is persisted even if the
     * outer transaction rolls back (e.g. if something fails after the audit call).
     * The performing user comes from the security context ("system" for background jobs); the
     * organization is passed explicitly so system jobs can tag entries too.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long organizationId, String action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        String user = CurrentUser.get().map(TenantPrincipal::getUsername).orElse("system");

        AuditLog entry = AuditLog.builder()
                .organizationId(organizationId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(user)
                .build();

        auditLogRepository.save(entry);
        log.info("AUDIT: {} {} #{} by {} [{} -> {}]", action, entityType, entityId, user, oldValue, newValue);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsForEntity(String entityType, Long entityId, Long organizationId) {
        return auditLogRepository.findByEntityTypeAndEntityIdAndOrganizationIdOrderByPerformedAtDesc(
                entityType, entityId, organizationId);
    }
}
