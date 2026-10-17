package com.incidentpilot.service;

import com.incidentpilot.dto.CreateTimelineEventRequest;
import com.incidentpilot.dto.TimelineEventResponse;
import com.incidentpilot.exception.IncidentNotFoundException;
import com.incidentpilot.model.*;
import com.incidentpilot.repository.IncidentRepository;
import com.incidentpilot.repository.TimelineEventRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimelineEventServiceTest {

    @Mock
    private TimelineEventRepository timelineEventRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private TimelineEventService timelineEventService;

    @Test
    @DisplayName("Add manual timeline event to an incident")
    void addEvent() {
        Incident incident = buildIncident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        TimelineEvent savedEvent = TimelineEvent.builder()
                .id(1L)
                .incident(incident)
                .type(TimelineEventType.COMMENT)
                .message("Checking database logs")
                .author("on-call-eng")
                .createdAt(LocalDateTime.now())
                .build();
        when(timelineEventRepository.save(any(TimelineEvent.class))).thenReturn(savedEvent);

        CreateTimelineEventRequest request = CreateTimelineEventRequest.builder()
                .type(TimelineEventType.COMMENT)
                .message("Checking database logs")
                .author("on-call-eng")
                .build();

        TimelineEventResponse response = timelineEventService.addEvent(1L, request);

        assertThat(response.getMessage()).isEqualTo("Checking database logs");
        assertThat(response.getType()).isEqualTo(TimelineEventType.COMMENT);
        assertThat(response.getAuthor()).isEqualTo("on-call-eng");
    }

    @Test
    @DisplayName("Add event to non-existent incident throws IncidentNotFoundException")
    void addEventToMissingIncident() {
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        CreateTimelineEventRequest request = CreateTimelineEventRequest.builder()
                .type(TimelineEventType.COMMENT)
                .message("test")
                .author("eng")
                .build();

        assertThatThrownBy(() -> timelineEventService.addEvent(99L, request))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    @DisplayName("Record status change creates a STATUS_CHANGE event with system author")
    void recordStatusChange() {
        Incident incident = buildIncident(1L);

        timelineEventService.recordStatusChange(incident, IncidentStatus.TRIGGERED, IncidentStatus.ACKNOWLEDGED);

        verify(timelineEventRepository).save(argThat(event -> {
            assertThat(event.getType()).isEqualTo(TimelineEventType.STATUS_CHANGE);
            assertThat(event.getAuthor()).isEqualTo("system");
            assertThat(event.getMessage()).contains("TRIGGERED").contains("ACKNOWLEDGED");
            return true;
        }));
    }

    @Test
    @DisplayName("Get timeline returns events ordered by creation time")
    void getTimeline() {
        Incident incident = buildIncident(1L);
        when(incidentRepository.existsById(1L)).thenReturn(true);

        List<TimelineEvent> events = List.of(
                TimelineEvent.builder().id(1L).incident(incident)
                        .type(TimelineEventType.STATUS_CHANGE).message("Status changed")
                        .author("system").createdAt(LocalDateTime.now().minusMinutes(10)).build(),
                TimelineEvent.builder().id(2L).incident(incident)
                        .type(TimelineEventType.COMMENT).message("Investigating DB")
                        .author("eng").createdAt(LocalDateTime.now()).build()
        );
        when(timelineEventRepository.findByIncidentIdOrderByCreatedAtAsc(1L)).thenReturn(events);

        List<TimelineEventResponse> timeline = timelineEventService.getTimeline(1L);

        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(0).getType()).isEqualTo(TimelineEventType.STATUS_CHANGE);
        assertThat(timeline.get(1).getType()).isEqualTo(TimelineEventType.COMMENT);
    }

    @Test
    @DisplayName("Get timeline for non-existent incident throws IncidentNotFoundException")
    void getTimelineForMissingIncident() {
        when(incidentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> timelineEventService.getTimeline(99L))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    private Incident buildIncident(Long id) {
        return Incident.builder()
                .id(id)
                .title("Test incident")
                .severity(Severity.P2_HIGH)
                .status(IncidentStatus.TRIGGERED)
                .serviceName("test-service")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
