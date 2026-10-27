package com.incidentpilot.model;

/**
 * Incident severity levels following the standard P1-P4 convention.
 * P1 = system is down, all hands on deck.
 * P4 = minor issue, fix when convenient.
 */
public enum Severity {
    P1_CRITICAL,
    P2_HIGH,
    P3_MEDIUM,
    P4_LOW
}
