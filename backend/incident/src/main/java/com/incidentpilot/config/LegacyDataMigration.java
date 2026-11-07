package com.incidentpilot.config;

import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.OrganizationRepository;
import com.incidentpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * One-time, idempotent backfill for data created before multi-tenancy.
 *
 * <p>Hibernate's {@code ddl-auto=update} adds the new {@code organization_id} columns as nullable.
 * On startup, any user/incident/audit/document row without an organization is moved into a
 * "Default Organization" so existing data keeps working. The earliest user of that organization is
 * promoted to ADMIN when it has none, so someone can see the invite code. Re-running is a no-op
 * once nothing is left to migrate.
 *
 * <p>The documents table belongs to the ingestion service but lives in the same database; it is
 * updated on a best-effort basis (skipped with a log line if the column does not exist yet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyDataMigration implements ApplicationRunner {

    static final String DEFAULT_ORG_NAME = "Default Organization";

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Value("${app.legacy-migration.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> migrate());
    }

    private void migrate() {
        long orphanUsers = count("SELECT COUNT(*) FROM users WHERE organization_id IS NULL");
        long orphanIncidents = count("SELECT COUNT(*) FROM incidents WHERE organization_id IS NULL");

        if (orphanUsers > 0 || orphanIncidents > 0) {
            Organization org = organizationRepository.findByNameIgnoreCase(DEFAULT_ORG_NAME)
                    .orElseGet(() -> organizationRepository.save(Organization.builder()
                            .name(DEFAULT_ORG_NAME)
                            .inviteCode(newInviteCode())
                            .build()));

            int users = jdbcTemplate.update("UPDATE users SET organization_id = ? WHERE organization_id IS NULL", org.getId());
            int incidents = jdbcTemplate.update("UPDATE incidents SET organization_id = ? WHERE organization_id IS NULL", org.getId());
            // createdBy was not tracked before; the audit trail knows who created each incident
            jdbcTemplate.update("UPDATE incidents SET created_by = (SELECT a.performed_by FROM audit_logs a "
                    + "WHERE a.entity_type = 'Incident' AND a.entity_id = incidents.id AND a.action = 'CREATE' "
                    + "ORDER BY a.id LIMIT 1) WHERE created_by IS NULL");
            log.info("Multi-tenancy migration: moved {} users and {} incidents into '{}'", users, incidents, DEFAULT_ORG_NAME);

            promoteFirstUserIfNoAdmin(org);
        }

        jdbcTemplate.update("UPDATE audit_logs SET organization_id = "
                + "(SELECT i.organization_id FROM incidents i WHERE i.id = audit_logs.entity_id) "
                + "WHERE organization_id IS NULL AND entity_type = 'Incident'");

        organizationRepository.findByNameIgnoreCase(DEFAULT_ORG_NAME)
                .ifPresent(org -> backfillDocuments(org.getId()));
    }

    private void promoteFirstUserIfNoAdmin(Organization org) {
        if (!userRepository.findByOrganizationIdAndRole(org.getId(), Role.ADMIN).isEmpty()) {
            return;
        }
        List<User> members = userRepository.findByOrganizationIdOrderByUsernameAsc(org.getId());
        members.stream().min(java.util.Comparator.comparing(User::getId)).ifPresent(first -> {
            first.setRole(Role.ADMIN);
            userRepository.save(first);
            log.info("Promoted '{}' to ADMIN of '{}'", first.getUsername(), DEFAULT_ORG_NAME);
        });
    }

    private void backfillDocuments(Long orgId) {
        try {
            int docs = jdbcTemplate.update("UPDATE documents SET organization_id = ? WHERE organization_id IS NULL", orgId);
            if (docs > 0) {
                log.info("Multi-tenancy migration: moved {} documents into '{}'", docs, DEFAULT_ORG_NAME);
            }
        } catch (DataAccessException e) {
            log.info("Documents table not migrated yet (ingestion service has not created the column): {}",
                    e.getMostSpecificCause().getMessage());
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private String newInviteCode() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
