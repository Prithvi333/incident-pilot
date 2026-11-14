package com.incidentpilot.service;

import com.incidentpilot.TestSecurity;
import com.incidentpilot.client.AiServiceClient;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.UpdateIncidentRequest;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidAssigneeException;
import com.incidentpilot.exception.InvalidTransitionException;
import com.incidentpilot.config.TenantCache;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.Severity;
import com.incidentpilot.repository.IncidentRepository;
import com.incidentpilot.repository.OrganizationRepository;
import com.incidentpilot.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tenant isolation, creation, assignment, escalation and lifecycle rules of {@link IncidentService}. */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock IncidentRepository incidentRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock TimelineEventService timelineEventService;
    @Mock AuditService auditService;
    @Mock IncidentActivityService activityService;
    @Mock OutboxService outboxService;
    @Mock TenantCache cache;
    @Mock AiServiceClient aiServiceClient;
    @Mock HttpServletRequest httpRequest;
    @InjectMocks IncidentService incidentService;

    private final Organization acme = TestSecurity.org(1);

    @BeforeEach
    void loginAsAcmeEngineer() {
        TestSecurity.login(TestSecurity.user(10, "alice", Role.ENGINEER, acme));
        // the cache is a pass-through here: always run the loader (the database lookup)
        lenient().when(cache.get(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(5)).get());
    }

    @AfterEach
    void clearContext() {
        TestSecurity.clear();
    }

    @Test
    void incidentOfAnotherOrganizationIsNotFound() {
        when(incidentRepository.findByIdAndOrganizationId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident(99L)).isInstanceOf(IncidentNotFoundException.class);
        // the lookup is always scoped to the caller's organization
        verify(incidentRepository).findByIdAndOrganizationId(99L, 1L);
        verify(incidentRepository, never()).findById(any());
    }

    @Test
    void createIncidentUsesPrincipalOrganizationAndReporter() {
        when(organizationRepository.getReferenceById(1L)).thenReturn(acme);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
            Incident i = inv.getArgument(0);
            i.setId(5L);
            return i;
        });
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title(" DB down ").severity(Severity.P2_HIGH).serviceName("db").build();

        IncidentResponse response = incidentService.createIncident(request);

        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(saved.capture());
        assertThat(saved.getValue().getOrganization().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("alice");
        assertThat(saved.getValue().getStatus()).isEqualTo(IncidentStatus.TRIGGERED);
        assertThat(response.getTitle()).isEqualTo("DB down");

        // the embedding event goes through the transactional outbox (same transaction as the incident)
        verify(outboxService).enqueueUpsert(any(Incident.class));
        verify(activityService).incidentCreated(any(Incident.class), eq("alice"));
    }

    @Test
    void assigneeOutsideTheOrganizationIsRejected() {
        when(userRepository.findByOrganizationIdAndUsername(1L, "mallory")).thenReturn(Optional.empty());
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("t").severity(Severity.P3_MEDIUM).serviceName("db").assignee("mallory").build();

        assertThatThrownBy(() -> incidentService.createIncident(request)).isInstanceOf(InvalidAssigneeException.class);
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void invalidStatusTransitionIsRejectedWithoutSaving() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P2_HIGH);
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.updateStatus(5L, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidTransitionException.class);
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolvingSetsResolvedAtAndNotifies() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.INVESTIGATING, Severity.P2_HIGH);
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.updateStatus(5L, IncidentStatus.RESOLVED);

        assertThat(incident.getResolvedAt()).isNotNull();
        verify(activityService).statusChanged(incident, IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED, "alice");
    }

    @Test
    void reopeningClearsResolutionAndRearmsTheResolveSlaAlert() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.RESOLVED, Severity.P2_HIGH);
        incident.setResolvedAt(LocalDateTime.now());
        incident.setResolveBreachNotified(true);
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.updateStatus(5L, IncidentStatus.INVESTIGATING);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.isResolveBreachNotified()).isFalse();
    }

    @Test
    void assigningToAMemberNotifiesAndRecordsThePreviousAssignee() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P2_HIGH);
        incident.setAssignee("bob");
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));
        when(userRepository.findByOrganizationIdAndUsername(1L, "carol"))
                .thenReturn(Optional.of(TestSecurity.user(12, "carol", Role.ENGINEER, acme)));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.assign(5L, "carol");

        assertThat(incident.getAssignee()).isEqualTo("carol");
        verify(activityService).assigned(incident, "bob", "alice");
    }

    @Test
    void assigningTheSamePersonAgainDoesNothing() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P2_HIGH);
        incident.setAssignee("carol");
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));
        when(userRepository.findByOrganizationIdAndUsername(1L, "carol"))
                .thenReturn(Optional.of(TestSecurity.user(12, "carol", Role.ENGINEER, acme)));

        incidentService.assign(5L, "carol");

        verify(incidentRepository, never()).saveAndFlush(any());
        verify(activityService, never()).assigned(any(), any(), any());
    }

    @Test
    void raisingSeverityEscalatesAndLoweringDoesNot() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P3_MEDIUM);
        when(incidentRepository.findByIdAndOrganizationId(5L, 1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.updateIncident(5L, UpdateIncidentRequest.builder().severity(Severity.P1_CRITICAL).build());
        verify(activityService).escalated(eq(incident), eq(Severity.P3_MEDIUM), eq(Severity.P1_CRITICAL),
                eq(List.of("severity")), eq("alice"));

        incidentService.updateIncident(5L, UpdateIncidentRequest.builder().severity(Severity.P4_LOW).build());
        verify(activityService).updated(eq(incident), eq(List.of("severity")), eq("alice"));
    }
}
