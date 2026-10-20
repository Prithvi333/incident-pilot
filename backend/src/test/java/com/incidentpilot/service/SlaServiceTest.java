package com.incidentpilot.service;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SlaService slaService;

    @Test
    @DisplayName("P1 incident not acknowledged within 15 min triggers SLA breach")
    void p1AcknowledgeBreach() {
        Incident stale = Incident.builder()
                .id(1L).title("P1 stale").severity(Severity.P1_CRITICAL)
                .status(IncidentStatus.TRIGGERED)
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .build();

        when(incidentRepository.findByStatusIn(anyCollection())).thenReturn(List.of(stale));

        slaService.checkSlaBreaches();

        verify(auditService).log(eq("SLA_BREACH_ACK"), eq("Incident"), eq(1L), anyString(), anyString());
    }

    @Test
    @DisplayName("P2 incident acknowledged but not resolved within 12h triggers breach")
    void p2ResolveBreach() {
        Incident investigating = Incident.builder()
                .id(2L).title("P2 slow").severity(Severity.P2_HIGH)
                .status(IncidentStatus.INVESTIGATING)
                .createdAt(LocalDateTime.now().minusHours(13))
                .build();

        when(incidentRepository.findByStatusIn(anyCollection())).thenReturn(List.of(investigating));

        slaService.checkSlaBreaches();

        verify(auditService).log(eq("SLA_BREACH_RESOLVE"), eq("Incident"), eq(2L), anyString(), anyString());
    }

    @Test
    @DisplayName("Fresh P1 incident within SLA does not trigger breach")
    void noBreachWhenWithinSla() {
        Incident fresh = Incident.builder()
                .id(3L).title("Fresh P1").severity(Severity.P1_CRITICAL)
                .status(IncidentStatus.TRIGGERED)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(incidentRepository.findByStatusIn(anyCollection())).thenReturn(List.of(fresh));

        slaService.checkSlaBreaches();

        verify(auditService, never()).log(anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("No open incidents means no breaches")
    void noOpenIncidents() {
        when(incidentRepository.findByStatusIn(anyCollection())).thenReturn(List.of());

        slaService.checkSlaBreaches();

        verifyNoInteractions(auditService);
    }
}
