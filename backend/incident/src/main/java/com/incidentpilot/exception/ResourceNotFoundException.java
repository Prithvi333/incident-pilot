package com.incidentpilot.exception;

/** Generic 404 for tenant-scoped resources (also used when a resource belongs to another tenant). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
