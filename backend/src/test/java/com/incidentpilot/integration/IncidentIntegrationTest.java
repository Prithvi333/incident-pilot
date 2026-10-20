package com.incidentpilot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.dto.AuthResponse;
import com.incidentpilot.dto.CreateIncidentRequest;
import com.incidentpilot.dto.RegisterRequest;
import com.incidentpilot.dto.UpdateStatusRequest;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.junit.jupiter.api.Tag("integration")
class IncidentIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("incidentpilot_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String uniqueUser = "integuser_" + System.nanoTime();
        RegisterRequest register = RegisterRequest.builder()
                .username(uniqueUser)
                .password("TestPass123!")
                .email(uniqueUser + "@test.com")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponse.class);
        token = auth.getToken();
    }

    @Test
    @DisplayName("Full incident lifecycle: create -> acknowledge -> investigate -> resolve")
    void fullIncidentLifecycle() throws Exception {
        CreateIncidentRequest createReq = CreateIncidentRequest.builder()
                .title("MySQL connection pool exhaustion")
                .description("All connections consumed, queries timing out")
                .severity(Severity.P1_CRITICAL)
                .serviceName("order-service")
                .assignee("oncall-eng")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIGGERED"))
                .andReturn();

        Long incidentId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        transitionStatus(incidentId, IncidentStatus.ACKNOWLEDGED);
        transitionStatus(incidentId, IncidentStatus.INVESTIGATING);
        transitionStatus(incidentId, IncidentStatus.RESOLVED);

        mockMvc.perform(get("/api/incidents/" + incidentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Invalid status transition returns 400")
    void invalidTransitionReturns400() throws Exception {
        Long id = createTestIncident();

        UpdateStatusRequest badTransition = UpdateStatusRequest.builder()
                .status(IncidentStatus.RESOLVED)
                .build();

        mockMvc.perform(patch("/api/incidents/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badTransition)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Stats endpoint returns correct counts")
    void statsEndpoint() throws Exception {
        createTestIncident();
        createTestIncident();

        MvcResult result = mockMvc.perform(get("/api/incidents/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("totalIncidents").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Timeline auto-populates on status change")
    void timelineAutoPopulates() throws Exception {
        Long id = createTestIncident();
        transitionStatus(id, IncidentStatus.ACKNOWLEDGED);

        mockMvc.perform(get("/api/incidents/" + id + "/timeline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGE"));
    }

    private Long createTestIncident() throws Exception {
        CreateIncidentRequest req = CreateIncidentRequest.builder()
                .title("Test incident " + System.nanoTime())
                .severity(Severity.P2_HIGH)
                .serviceName("test-service")
                .build();

        MvcResult result = mockMvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transitionStatus(Long id, IncidentStatus status) throws Exception {
        UpdateStatusRequest req = UpdateStatusRequest.builder().status(status).build();
        mockMvc.perform(patch("/api/incidents/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
