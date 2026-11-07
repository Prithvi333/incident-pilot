package com.incidentpilot.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static accessor for the authenticated {@link TenantPrincipal} of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<TenantPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** Returns the principal or fails with an authentication error (mapped to 401). */
    public static TenantPrincipal require() {
        return get().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("Authentication is required"));
    }
}
