package com.incidentpilot.model;

/** Kinds of activity that generate a persistent notification for a user. */
public enum NotificationType {
    INCIDENT_CREATED,
    INCIDENT_ASSIGNED,
    INCIDENT_REASSIGNED,
    INCIDENT_UPDATED,
    INCIDENT_ESCALATED,
    STATUS_CHANGED,
    INCIDENT_RESOLVED,
    INCIDENT_REOPENED,
    COMMENT_ADDED,
    SLA_BREACH
}
