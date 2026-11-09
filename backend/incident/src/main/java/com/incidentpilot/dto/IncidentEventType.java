package com.incidentpilot.dto;

/** Types of real-time events pushed over WebSocket to the organization topic. */
public enum IncidentEventType {
    INCIDENT_CREATED,
    INCIDENT_UPDATED,
    INCIDENT_ASSIGNED,
    INCIDENT_ESCALATED,
    STATUS_CHANGED,
    INCIDENT_RESOLVED,
    INCIDENT_REOPENED,
    COMMENT_ADDED,
    SLA_BREACH
}
