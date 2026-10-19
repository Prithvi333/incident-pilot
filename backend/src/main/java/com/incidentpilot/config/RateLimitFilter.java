package com.incidentpilot.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isRateLimitedPath(path)) {
            String key = resolveKey(request);
            Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":429,\"error\":\"Rate limit exceeded. Try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedPath(String path) {
        return path.contains("/similar") || path.startsWith("/api/documents");
    }

    private String resolveKey(HttpServletRequest request) {
        String user = request.getRemoteUser();
        if (user != null) {
            return "user:" + user;
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(20).refillGreedy(20, Duration.ofMinutes(1)))
                .addLimit(limit -> limit.capacity(100).refillGreedy(100, Duration.ofHours(1)))
                .build();
    }
}
