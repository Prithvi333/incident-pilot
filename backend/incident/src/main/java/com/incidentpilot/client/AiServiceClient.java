package com.incidentpilot.client;

import com.incidentpilot.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Calls the AI service on behalf of the user, forwarding their JWT. The AI service takes the
 * organization from that token and filters its vector search by it, so results never cross tenants.
 * If the AI service is down or slow the caller gets a graceful "AI unavailable" response, not a 500.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.ai.url}")
    private String aiServiceUrl;

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

        try {
            ResponseEntity<RagResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    RagResponse.class
            );
            return response.getBody() != null ? response.getBody() : unavailable();
        } catch (RestClientException e) {
            log.warn("AI service call failed for incident #{}: {}", incidentId, e.getMessage());
            return unavailable();
        }
    }

    private RagResponse unavailable() {
        return RagResponse.builder()
                .similarMatches(List.of())
                .resolutionSuggestion("AI service is currently unavailable. Please try again later.")
                .aiAvailable(false)
                .build();
    }
}
