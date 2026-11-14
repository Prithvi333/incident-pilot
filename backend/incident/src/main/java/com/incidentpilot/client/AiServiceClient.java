package com.incidentpilot.client;

import com.incidentpilot.dto.RagResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Calls the AI service on behalf of the user, forwarding their JWT. The AI service takes the
 * organization from that token and filters its vector search by it, so results never cross tenants.
 *
 * <p>The call is wrapped in a circuit breaker with short timeouts: when the AI service is slow or
 * down, calls fail fast (after a few failures the breaker opens and calls return immediately) and the
 * caller gets an "AI unavailable" response instead of an error or a hung request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.ai.url}")
    private String aiServiceUrl;

    @CircuitBreaker(name = "ai-service", fallbackMethod = "unavailable")
    public RagResponse findSimilarIncidents(
            Long incidentId,
            String title,
            String description,
            String token) {

        String url = aiServiceUrl
                + "/api/ai/incidents/" + incidentId + "/similar"
                + "?title=" + UriUtils.encodeQueryParam(
                title == null ? "" : title,
                StandardCharsets.UTF_8)
                + "&description=" + UriUtils.encodeQueryParam(
                description == null ? "" : description,
                StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<RagResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                RagResponse.class
        );
        if (response.getBody() == null) {
            throw new IllegalStateException("Empty response from AI service");
        }
        return response.getBody();
    }

    /** Circuit-breaker fallback: any failure (timeout, 5xx, open breaker) degrades to "AI unavailable". */
    @SuppressWarnings("unused")
    public RagResponse unavailable(Long incidentId, String title, String description, String token, Throwable cause) {
        log.warn("AI service unavailable for incident #{}: {}", incidentId, cause.toString());
        return RagResponse.builder()
                .similarMatches(List.of())
                .resolutionSuggestion("AI insights are currently unavailable.")
                .aiAvailable(false)
                .build();
    }
}
