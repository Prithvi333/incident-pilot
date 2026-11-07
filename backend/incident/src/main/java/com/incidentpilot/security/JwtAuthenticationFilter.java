package com.incidentpilot.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * Extracts and validates the JWT from the {@code Authorization: Bearer ...} header.
 *
 * <p>Runs once per request before Spring Security's authorization checks. The user (and therefore
 * the organization) is always loaded from the database; the token's {@code orgId} claim, when
 * present, must match it. Invalid or missing tokens are ignored — the request continues
 * unauthenticated and Spring Security decides whether to allow or deny it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseClaims(authHeader.substring(7));
            String username = claims.getSubject();

            if (username != null && claims.getExpiration().after(new Date())
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (userDetails instanceof TenantPrincipal principal && tokenMatchesOrganization(claims, principal)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid/expired token or unknown user — continue without authentication
            log.debug("JWT rejected: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /** Tokens issued before multi-tenancy have no orgId claim and are accepted; others must match. */
    private boolean tokenMatchesOrganization(Claims claims, TenantPrincipal principal) {
        Number orgClaim = claims.get(JwtService.CLAIM_ORG_ID, Number.class);
        return orgClaim == null || orgClaim.longValue() == principal.getOrganizationId();
    }
}
