package com.incidentpilot.model;

/**
 * Types of events that can appear in an incident timeline.
 *
 * STATUS_CHANGE — auto-generated when incident transitions (e.g. TRIGGERED → ACKNOWLEDGED)
 * COMMENT      — manual note from an engineer ("Checked DB logs, pool at 100%")
 * ACTION       — a specific action taken ("Restarted payment-service pods")
 */
public enum TimelineEventType {
    STATUS_CHANGE,
    COMMENT,
    ACTION
}
