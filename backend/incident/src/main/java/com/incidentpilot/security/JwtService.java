package com.incidentpilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT token generation and validation using HMAC-SHA256.
 *
 * <p>Tokens carry the username (subject), role and organization id (custom claims). The same
 * signing key is shared with the ai and ingestion services, which read {@code orgId} to scope
 * their data. The signing key is Base64-encoded in config to avoid encoding issues.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ORG_ID = "orgId";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(String username, String role, Long organizationId) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_ORG_ID, organizationId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /** Verifies the signature and expiry, returning the claims. Throws a JwtException if invalid. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Organization id from the token, or null for tokens issued before multi-tenancy. */
    public Long extractOrganizationId(String token) {
        Number value = parseClaims(token).get(CLAIM_ORG_ID, Number.class);
        return value == null ? null : value.longValue();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = parseClaims(token);
        return userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
