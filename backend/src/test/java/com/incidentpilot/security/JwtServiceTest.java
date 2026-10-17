package com.incidentpilot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "aW5jaWRlbnRwaWxvdC1kZXYtc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1obWFjLXNoYTI1Ng==");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
    }

    private UserDetails buildUserDetails(String username, String role) {
        return new User(username, "password",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("Generated token contains correct username")
    void extractUsername() {
        String token = jwtService.generateToken("alice", "ENGINEER");
        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    @DisplayName("Generated token contains correct role")
    void extractRole() {
        String token = jwtService.generateToken("alice", "ADMIN");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Token is valid for matching user")
    void isTokenValid() {
        String token = jwtService.generateToken("alice", "ENGINEER");
        UserDetails userDetails = buildUserDetails("alice", "ENGINEER");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    @DisplayName("Token is invalid for different user")
    void isTokenInvalidForWrongUser() {
        String token = jwtService.generateToken("alice", "ENGINEER");
        UserDetails userDetails = buildUserDetails("bob", "ENGINEER");
        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    @DisplayName("Expired token is invalid")
    void expiredTokenIsInvalid() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String token = jwtService.generateToken("alice", "ENGINEER");
        UserDetails userDetails = buildUserDetails("alice", "ENGINEER");

        assertThatThrownBy(() -> jwtService.isTokenValid(token, userDetails))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }
}
