package com.incidentpilot.exception;

public class DuplicateOrganizationException extends RuntimeException {
    public DuplicateOrganizationException(String name) {
        super("Organization name already taken: " + name);
    }
}
