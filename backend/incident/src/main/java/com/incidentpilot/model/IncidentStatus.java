package com.incidentpilot.model;

import java.util.Map;
import java.util.Set;

/**
 * Incident lifecycle as a state machine.
 *
 * Valid transitions:
 *   TRIGGERED → ACKNOWLEDGED
 *   ACKNOWLEDGED → INVESTIGATING
 *   INVESTIGATING → RESOLVED
 *   RESOLVED → POSTMORTEM
 *   RESOLVED → INVESTIGATING   (reopen: the fix did not hold)
 *
 * Why a state machine? A simple string status lets anyone set any value
 * (e.g. jump from TRIGGERED straight to RESOLVED, skipping investigation).
 * The state machine enforces that incidents follow a real workflow,
 * which prevents data inconsistency and makes audit logs meaningful.
 */
public enum IncidentStatus {

    TRIGGERED,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED,
    POSTMORTEM;

    /**
     * Each status maps to the set of statuses it can transition TO.
     * If a transition isn't in this map, it's not allowed.
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> ALLOWED_TRANSITIONS = Map.of(
            TRIGGERED, Set.of(ACKNOWLEDGED),
            ACKNOWLEDGED, Set.of(INVESTIGATING),
            INVESTIGATING, Set.of(RESOLVED),
            RESOLVED, Set.of(POSTMORTEM, INVESTIGATING)
            // POSTMORTEM has no outgoing transitions — it's the terminal state
    );

    /** A move from RESOLVED back to INVESTIGATING is a reopen rather than a normal forward step. */
    public static boolean isReopen(IncidentStatus from, IncidentStatus to) {
        return from == RESOLVED && to == INVESTIGATING;
    }

    /**
     * Check if moving from this status to the target status is allowed.
     */
    public boolean canTransitionTo(IncidentStatus target) {
        Set<IncidentStatus> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
