package com.incidentpilot.exception;

public class InvalidInviteCodeException extends RuntimeException {
    public InvalidInviteCodeException() {
        super("Invalid invite code");
    }
}
