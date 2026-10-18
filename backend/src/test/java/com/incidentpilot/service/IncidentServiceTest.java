package com.incidentpilot.service;

import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.IncidentResponse;
import com.incidentpilot.dto.IncidentStatsResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.exception.InvalidTransitionException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private TimelineEventService timelineEventService;

    @Mock
    private RagService ragService;

    @InjectMocks
    private IncidentService incidentService;

    @Test
    @DisplayName("Create incident sets status to TRIGGERED and returns response")
    void createIncident() {
        CreateIncidentRequest request = CreateIncidentRequest.builder()
                .title("Database connection pool exhausted")
                .description("MySQL connection pool at 100%, queries timing out")
                .severity(Severity.P1_CRITICAL)
                .assignee("on-call-eng")
                .serviceName("payment-service")
                .build();

        Incident savedIncident = Incident.builder()
                .id(1L)
                .title(request.getTitle())
                .description(request.getDescription())
                .severity(request.getSeverity())
                .status(IncidentStatus.TRIGGERED)
                .assignee(request.getAssignee())
                .serviceName(request.getServiceName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(incidentRepository.save(any(Incident.class))).thenReturn(savedIncident);

        IncidentResponse response = incidentService.createIncident(request);

        assertThat(response.getTitle()).isEqualTo("Database connection pool exhausted");
        assertThat(response.getStatus()).isEqualTo(IncidentStatus.TRIGGERED);
        assertThat(response.getSeverity()).isEqualTo(Severity.P1_CRITICAL);
        assertThat(response.getServiceName()).isEqualTo("payment-service");
    }

    @Test
    @DisplayName("Get incident by ID returns the incident")
    void getIncident() {
        Incident incident = buildIncident(1L, IncidentStatus.TRIGGERED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        IncidentResponse response = incidentService.getIncident(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Test incident");
    }

    @Test
    @DisplayName("Get incident with unknown ID throws IncidentNotFoundException")
    void getIncidentNotFound() {
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident(99L))
                .isInstanceOf(IncidentNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Valid status transition succeeds")
    void updateStatusValid() {
        Incident incident = buildIncident(1L, IncidentStatus.TRIGGERED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        IncidentResponse response = incidentService.updateStatus(1L, IncidentStatus.ACKNOWLEDGED);

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Invalid status transition throws InvalidTransitionException")
    void updateStatusInvalid() {
        Incident incident = buildIncident(1L, IncidentStatus.TRIGGERED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.updateStatus(1L, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("TRIGGERED")
                .hasMessageContaining("RESOLVED");
    }

    @Test
    @DisplayName("Resolving an incident sets resolvedAt timestamp")
    void updateStatusToResolvedSetsTimestamp() {
        Incident incident = buildIncident(1L, IncidentStatus.INVESTIGATING);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentResponse response = incidentService.updateStatus(1L, IncidentStatus.RESOLVED);

        assertThat(response.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Get all incidents returns list of responses")
    void getAllIncidents() {
        List<Incident> incidents = List.of(
                buildIncident(1L, IncidentStatus.TRIGGERED),
                buildIncident(2L, IncidentStatus.INVESTIGATING)
        );
        when(incidentRepository.findAll()).thenReturn(incidents);

        List<IncidentResponse> responses = incidentService.getAllIncidents();

        assertThat(responses).hasSize(2);
    }

    @Test
    @DisplayName("Status transition auto-creates a timeline entry")
    void updateStatusCreatesTimelineEvent() {
        Incident incident = buildIncident(1L, IncidentStatus.TRIGGERED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenReturn(incident);

        incidentService.updateStatus(1L, IncidentStatus.ACKNOWLEDGED);

        verify(timelineEventService).recordStatusChange(
                any(Incident.class),
                eq(IncidentStatus.TRIGGERED),
                eq(IncidentStatus.ACKNOWLEDGED)
        );
    }

    @Test
    @DisplayName("Filter incidents by status returns matching incidents")
    void getFilteredByStatus() {
        List<Incident> triggered = List.of(buildIncident(1L, IncidentStatus.TRIGGERED));
        when(incidentRepository.findByStatus(IncidentStatus.TRIGGERED)).thenReturn(triggered);

        List<IncidentResponse> results = incidentService.getFilteredIncidents(IncidentStatus.TRIGGERED, null);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Filter incidents by severity returns matching incidents")
    void getFilteredBySeverity() {
        List<Incident> p1s = List.of(buildIncident(1L, IncidentStatus.TRIGGERED));
        when(incidentRepository.findBySeverity(Severity.P2_HIGH)).thenReturn(p1s);

        List<IncidentResponse> results = incidentService.getFilteredIncidents(null, Severity.P2_HIGH);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Filter by both status and severity")
    void getFilteredByStatusAndSeverity() {
        when(incidentRepository.findByStatusAndSeverity(IncidentStatus.TRIGGERED, Severity.P1_CRITICAL))
                .thenReturn(List.of());

        List<IncidentResponse> results = incidentService.getFilteredIncidents(
                IncidentStatus.TRIGGERED, Severity.P1_CRITICAL);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Stats returns correct totals and breakdowns")
    void getStats() {
        LocalDateTime now = LocalDateTime.now();
        List<Incident> incidents = List.of(
                buildIncidentWithTimes(1L, IncidentStatus.TRIGGERED, Severity.P1_CRITICAL, now.minusHours(2), null),
                buildIncidentWithTimes(2L, IncidentStatus.RESOLVED, Severity.P2_HIGH, now.minusHours(3), now.minusHours(1)),
                buildIncidentWithTimes(3L, IncidentStatus.INVESTIGATING, Severity.P1_CRITICAL, now.minusHours(1), null)
        );
        when(incidentRepository.findAll()).thenReturn(incidents);

        IncidentStatsResponse stats = incidentService.getStats();

        assertThat(stats.getTotalIncidents()).isEqualTo(3);
        assertThat(stats.getActiveIncidents()).isEqualTo(2);
        assertThat(stats.getResolvedIncidents()).isEqualTo(1);
        assertThat(stats.getAverageResolutionMinutes()).isNotNull();
        assertThat(stats.getCountBySeverity().get("P1_CRITICAL")).isEqualTo(2);
        assertThat(stats.getCountByStatus().get("TRIGGERED")).isEqualTo(1);
    }

    private Incident buildIncidentWithTimes(Long id, IncidentStatus status, Severity severity,
                                             LocalDateTime createdAt, LocalDateTime resolvedAt) {
        return Incident.builder()
                .id(id).title("Incident " + id).severity(severity).status(status)
                .serviceName("svc").createdAt(createdAt).updatedAt(createdAt).resolvedAt(resolvedAt)
                .build();
    }

    /** Helper to build a test incident with sensible defaults */
    private Incident buildIncident(Long id, IncidentStatus status) {
        return Incident.builder()
                .id(id)
                .title("Test incident")
                .description("Test description")
                .severity(Severity.P2_HIGH)
                .status(status)
                .assignee("test-user")
                .serviceName("test-service")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
