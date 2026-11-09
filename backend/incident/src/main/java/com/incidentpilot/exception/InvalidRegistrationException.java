package com.incidentpilot.exception;

/** Registration must either create an organization or join one with an invite code (not both, not neither). */
public class InvalidRegistrationException extends RuntimeException {
    public InvalidRegistrationException(String message) {
        super(message);
    }
}
