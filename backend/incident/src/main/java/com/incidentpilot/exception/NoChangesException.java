package com.incidentpilot.exception;

public class NoChangesException extends RuntimeException {
    public NoChangesException() {
        super("At least one field must be provided");
    }
}
