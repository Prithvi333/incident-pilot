package com.incidentpilot.ingestion.security;

/** Identity taken from the verified JWT: who is calling and which organization they belong to. */
public record AuthenticatedUser(String username, Long organizationId, String role) {
}
