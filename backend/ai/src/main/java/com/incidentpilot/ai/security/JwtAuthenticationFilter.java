package com.incidentpilot.ai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;

/**
 * Validates the JWT issued by the incident service and exposes an {@link AuthenticatedUser}
 * (username, organization, role) as the principal. Tokens without an organization claim are
 * rejected, so every AI request is always tied to exactly one tenant.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(header.substring(7))
                    .getPayload();

            String username = claims.getSubject();
            Number orgClaim = claims.get("orgId", Number.class);

            if (username == null || username.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT subject is missing");
                return;
            }
            if (orgClaim == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "JWT has no organization; please sign in again");
                return;
            }

            String role = claims.get("role", String.class);
            AuthenticatedUser user = new AuthenticatedUser(username, orgClaim.longValue(), role);
            var authentication = new UsernamePasswordAuthenticationToken(
                    user, null,
                    role == null ? AuthorityUtils.NO_AUTHORITIES : AuthorityUtils.createAuthorityList("ROLE_" + role));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
