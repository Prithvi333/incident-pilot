package com.incidentpilot.client;

import com.incidentpilot.dto.RagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

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

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<RagResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                RagResponse.class
        );

        return response.getBody();
    }
}