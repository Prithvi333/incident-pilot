package com.incidentpilot.service;

import com.incidentpilot.dto.AuthResponse;
import com.incidentpilot.dto.LoginRequest;
import com.incidentpilot.dto.RegisterRequest;
import com.incidentpilot.exception.DuplicateUsernameException;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.UserRepository;
import com.incidentpilot.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("register creates user with ENGINEER role and returns token")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("securePass123");
        request.setEmail("alice@example.com");

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("securePass123")).thenReturn("$2a$encoded");
        when(jwtService.generateToken("alice", "ENGINEER")).thenReturn("jwt-token-123");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token-123");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("ENGINEER");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPassword()).isEqualTo("$2a$encoded");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ENGINEER);
    }

    @Test
    @DisplayName("register throws DuplicateUsernameException for existing username")
    void registerDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("securePass123");
        request.setEmail("alice@example.com");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(userRepository, never()).save(any());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    @DisplayName("login authenticates user and returns token")
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("password123");

        User user = User.builder()
                .id(1L).username("bob").role(Role.ADMIN).build();

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("bob", "ADMIN")).thenReturn("jwt-admin-token");

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-admin-token");
        assertThat(response.getUsername()).isEqualTo("bob");
        assertThat(response.getRole()).isEqualTo("ADMIN");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("bob", "password123"));
    }

    @Test
    @DisplayName("login throws when credentials are invalid")
    void loginBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
