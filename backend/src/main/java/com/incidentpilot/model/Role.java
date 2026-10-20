package com.incidentpilot.model;

/**
 * User access levels, checked by Spring Security's {@code hasRole()} expressions.
 *
 * <p>ADMIN — full access including document uploads.
 * ENGINEER — can create incidents and update status.
 * VIEWER — read-only access to incidents and documents.
 */
public enum Role {
    ADMIN,
    ENGINEER,
    VIEWER
}
