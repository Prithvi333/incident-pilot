package com.incidentpilot.exception;

import com.incidentpilot.model.IncidentStatus;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(IncidentStatus from, IncidentStatus to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
