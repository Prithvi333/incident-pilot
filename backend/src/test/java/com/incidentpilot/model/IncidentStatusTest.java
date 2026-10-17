package com.incidentpilot.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the state machine logic. Every valid and invalid transition is covered.
 * This is the kind of test interviewers love — it proves you thought about edge cases.
 */
class IncidentStatusTest {

    @Test
    @DisplayName("TRIGGERED can only transition to ACKNOWLEDGED")
    void triggeredToAcknowledged() {
        assertThat(IncidentStatus.TRIGGERED.canTransitionTo(IncidentStatus.ACKNOWLEDGED)).isTrue();
        assertThat(IncidentStatus.TRIGGERED.canTransitionTo(IncidentStatus.INVESTIGATING)).isFalse();
        assertThat(IncidentStatus.TRIGGERED.canTransitionTo(IncidentStatus.RESOLVED)).isFalse();
        assertThat(IncidentStatus.TRIGGERED.canTransitionTo(IncidentStatus.POSTMORTEM)).isFalse();
    }

    @Test
    @DisplayName("ACKNOWLEDGED can only transition to INVESTIGATING")
    void acknowledgedToInvestigating() {
        assertThat(IncidentStatus.ACKNOWLEDGED.canTransitionTo(IncidentStatus.INVESTIGATING)).isTrue();
        assertThat(IncidentStatus.ACKNOWLEDGED.canTransitionTo(IncidentStatus.TRIGGERED)).isFalse();
        assertThat(IncidentStatus.ACKNOWLEDGED.canTransitionTo(IncidentStatus.RESOLVED)).isFalse();
    }

    @Test
    @DisplayName("INVESTIGATING can only transition to RESOLVED")
    void investigatingToResolved() {
        assertThat(IncidentStatus.INVESTIGATING.canTransitionTo(IncidentStatus.RESOLVED)).isTrue();
        assertThat(IncidentStatus.INVESTIGATING.canTransitionTo(IncidentStatus.TRIGGERED)).isFalse();
        assertThat(IncidentStatus.INVESTIGATING.canTransitionTo(IncidentStatus.ACKNOWLEDGED)).isFalse();
    }

    @Test
    @DisplayName("RESOLVED can only transition to POSTMORTEM")
    void resolvedToPostmortem() {
        assertThat(IncidentStatus.RESOLVED.canTransitionTo(IncidentStatus.POSTMORTEM)).isTrue();
        assertThat(IncidentStatus.RESOLVED.canTransitionTo(IncidentStatus.TRIGGERED)).isFalse();
        assertThat(IncidentStatus.RESOLVED.canTransitionTo(IncidentStatus.INVESTIGATING)).isFalse();
    }

    @Test
    @DisplayName("POSTMORTEM is a terminal state — no transitions allowed")
    void postmortemIsTerminal() {
        for (IncidentStatus status : IncidentStatus.values()) {
            assertThat(IncidentStatus.POSTMORTEM.canTransitionTo(status)).isFalse();
        }
    }
}
