package com.incidentpilot.service;

import com.incidentpilot.TestSecurity;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SLA thresholds, one-time alerting and escalation in {@link SlaService}. */
@ExtendWith(MockitoExtension.class)
class SlaServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock AuditService auditService;
    @Mock IncidentActivityService activityService;
    @Mock PlatformTransactionManager transactionManager;

    private final Organization acme = TestSecurity.org(1);

    private SlaService service() {
        return new SlaService(incidentRepository, auditService, activityService, transactionManager);
    }

    private Incident openIncident(IncidentStatus status, Severity severity, long minutesOld) {
        Incident i = TestSecurity.incident(7, acme, status, severity);
        i.setCreatedAt(LocalDateTime.now().minusMinutes(minutesOld));
        when(incidentRepository.findIdsByStatusIn(any())).thenReturn(List.of(7L));
        when(incidentRepository.findById(7L)).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void unacknowledgedP1BreachIsEscalatedExactlyOnce() {
        Incident incident = openIncident(IncidentStatus.TRIGGERED, Severity.P1_CRITICAL, 20); // ack SLA is 15 min
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
        SlaService sla = service();

        sla.checkSlaBreaches();
        sla.checkSlaBreaches(); // the scheduler runs again a minute later

        verify(activityService, times(1)).slaBreach(any(Incident.class), eq("ACKNOWLEDGE"), anyString(), anyString());
        verify(auditService, times(1)).log(eq(1L), eq("SLA_BREACH_ACK"), eq("Incident"), eq(7L), anyString(), anyString());
        verify(activityService, never()).slaBreach(any(), eq("RESOLVE"), anyString(), anyString());
    }

    @Test
    void incidentWithinItsSlaIsLeftAlone() {
        openIncident(IncidentStatus.TRIGGERED, Severity.P1_CRITICAL, 5);

        service().checkSlaBreaches();

        verify(incidentRepository, never()).saveAndFlush(any());
        verify(activityService, never()).slaBreach(any(), anyString(), anyString(), anyString());
    }

    @Test
    void resolveSlaAppliesEvenToIncidentsNeverAcknowledged() {
        openIncident(IncidentStatus.TRIGGERED, Severity.P1_CRITICAL, 5 * 60); // 5h > 4h resolve SLA
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service().checkSlaBreaches();

        verify(activityService).slaBreach(any(Incident.class), eq("ACKNOWLEDGE"), anyString(), anyString());
        verify(activityService).slaBreach(any(Incident.class), eq("RESOLVE"), anyString(), anyString());
    }

    @Test
    void acknowledgedIncidentOnlyBreachesTheResolveSla() {
        openIncident(IncidentStatus.INVESTIGATING, Severity.P3_MEDIUM, 49 * 60); // 49h > 48h
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        service().checkSlaBreaches();

        verify(activityService, never()).slaBreach(any(), eq("ACKNOWLEDGE"), anyString(), anyString());
        verify(activityService).slaBreach(any(Incident.class), eq("RESOLVE"), anyString(), anyString());
    }

    @Test
    void reopenedIncidentRaisesTheResolveAlertAgain() {
        Incident incident = openIncident(IncidentStatus.INVESTIGATING, Severity.P1_CRITICAL, 5 * 60);
        incident.setResolveBreachNotified(true); // already alerted once
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
        SlaService sla = service();

        sla.checkSlaBreaches();
        verify(activityService, never()).slaBreach(any(), eq("RESOLVE"), anyString(), anyString());

        incident.setResolveBreachNotified(false); // what IncidentService.updateStatus does on reopen
        sla.checkSlaBreaches();
        verify(activityService).slaBreach(any(Incident.class), eq("RESOLVE"), anyString(), anyString());
    }
}
