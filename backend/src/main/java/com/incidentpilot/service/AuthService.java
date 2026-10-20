package com.incidentpilot.service;

import com.incidentpilot.dto.AuthResponse;
import com.incidentpilot.dto.LoginRequest;
import com.incidentpilot.dto.RegisterRequest;
import com.incidentpilot.exception.DuplicateUsernameException;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.UserRepository;
import com.incidentpilot.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login, issuing JWT tokens on success.
 *
 * <p>New users are assigned the ENGINEER role by default.
 * Password hashing uses BCrypt via Spring Security's {@link PasswordEncoder}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException(request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ENGINEER)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Delegates credential verification to Spring Security's AuthenticationManager.
     * If credentials are wrong, it throws BadCredentialsException — caught by GlobalExceptionHandler.
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow();

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}
