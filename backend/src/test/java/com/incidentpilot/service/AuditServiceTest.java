package com.incidentpilot.service;

import com.incidentpilot.model.AuditLog;
import com.incidentpilot.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("log() persists an audit entry with the authenticated user")
    void logWithAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        auditService.log("STATUS_CHANGE", "Incident", 42L, "TRIGGERED", "ACKNOWLEDGED");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("STATUS_CHANGE");
        assertThat(saved.getEntityType()).isEqualTo("Incident");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getOldValue()).isEqualTo("TRIGGERED");
        assertThat(saved.getNewValue()).isEqualTo("ACKNOWLEDGED");
        assertThat(saved.getPerformedBy()).isEqualTo("alice");

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("log() uses 'system' when no authentication present")
    void logWithoutAuthentication() {
        SecurityContextHolder.clearContext();

        auditService.log("CREATE", "Incident", 1L, null, "TRIGGERED");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("getLogsForEntity delegates to repository")
    void getLogsForEntity() {
        AuditLog entry = AuditLog.builder()
                .id(1L).action("CREATE").entityType("Incident").entityId(5L)
                .performedBy("bob").build();
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByPerformedAtDesc("Incident", 5L))
                .thenReturn(List.of(entry));

        List<AuditLog> logs = auditService.getLogsForEntity("Incident", 5L);

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getPerformedBy()).isEqualTo("bob");
    }
}
