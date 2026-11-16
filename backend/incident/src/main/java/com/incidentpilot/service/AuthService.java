package com.incidentpilot.service;

import com.incidentpilot.config.TenantCache;
import com.incidentpilot.dto.AuthResponse;
import com.incidentpilot.dto.LoginRequest;
import com.incidentpilot.dto.RegisterRequest;
import com.incidentpilot.exception.DuplicateOrganizationException;
import com.incidentpilot.exception.DuplicateUsernameException;
import com.incidentpilot.exception.InvalidInviteCodeException;
import com.incidentpilot.exception.InvalidRegistrationException;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.OrganizationRepository;
import com.incidentpilot.repository.UserRepository;
import com.incidentpilot.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Handles user registration and login, issuing JWT tokens on success.
 *
 * <p>Registration either creates a new organization (the user becomes its ADMIN) or joins an
 * existing one through its invite code (the user becomes an ENGINEER). Passwords are hashed with
 * BCrypt via Spring Security's {@link PasswordEncoder}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TenantCache cache;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String orgName = request.getOrganizationName() == null ? "" : request.getOrganizationName().trim();
        String inviteCode = request.getInviteCode() == null ? "" : request.getInviteCode().trim();

        if (orgName.isEmpty() == inviteCode.isEmpty()) {
            throw new InvalidRegistrationException(
                    "Provide either an organization name (to create one) or an invite code (to join one)");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
        }

        Organization organization;
        Role role;
        if (!orgName.isEmpty()) {
            if (organizationRepository.existsByNameIgnoreCase(orgName)) {
                throw new DuplicateOrganizationException(orgName);
            }
            organization = organizationRepository.save(Organization.builder()
                    .name(orgName)
                    .inviteCode(generateInviteCode())
                    .build());
            role = Role.ADMIN;
        } else {
            organization = organizationRepository.findByInviteCode(inviteCode)
                    .orElseThrow(InvalidInviteCodeException::new);
            role = Role.ENGINEER;
        }

        User user = userRepository.save(User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .organization(organization)
                .build());

        cache.invalidateMembers(organization.getId());
        return toResponse(user);
    }

    /**
     * Delegates credential verification to Spring Security's AuthenticationManager.
     * If credentials are wrong, it throws BadCredentialsException — caught by GlobalExceptionHandler.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        Organization org = user.getOrganization();
        String token = jwtService.generateToken(user.getUsername(), user.getRole().name(), org.getId());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .organizationId(org.getId())
                .organizationName(org.getName())
                .build();
    }

    private String generateInviteCode() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
