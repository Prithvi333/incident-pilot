package com.incidentpilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.dto.AuthResponse;
import com.incidentpilot.dto.LoginRequest;
import com.incidentpilot.dto.RegisterRequest;
import com.incidentpilot.exception.DuplicateUsernameException;
import com.incidentpilot.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@WithMockUser
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.incidentpilot.security.JwtService jwtService;

    @MockitoBean
    private com.incidentpilot.security.CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/auth/register — creates user and returns 201 with token")
    void register() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .password("secret123")
                .email("alice@example.com")
                .build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(AuthResponse.builder()
                .token("mock-jwt-token")
                .username("alice")
                .role("ENGINEER")
                .build());

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ENGINEER"));
    }

    @Test
    @DisplayName("POST /api/auth/register — duplicate username returns 409")
    void registerDuplicate() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice")
                .password("secret123")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateUsernameException("alice"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Username already taken: alice"));
    }

    @Test
    @DisplayName("POST /api/auth/register — blank username returns 400")
    void registerValidationFails() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("")
                .password("short")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/auth/login — valid credentials returns 200 with token")
    void login() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice")
                .password("secret123")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(AuthResponse.builder()
                .token("mock-jwt-token")
                .username("alice")
                .role("ENGINEER")
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("ENGINEER"));
    }

    @Test
    @DisplayName("POST /api/auth/login — bad credentials returns 401")
    void loginBadCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("alice")
                .password("wrong")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }
}
