package com.incidentpilot.exception;

/** The assignee is not a member of the caller's organization. */
public class InvalidAssigneeException extends RuntimeException {
    public InvalidAssigneeException(String username) {
        super("Assignee is not a member of your organization: " + username);
    }
}
