package com.incidentpilot.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentpilot.util.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Small Redis read-through cache whose every key starts with the organization id
 * ({@code ip:{orgId}:{kind}:{id}}), so one tenant can never read another tenant's entries.
 *
 * <p>Design rules:
 * <ul>
 *   <li>Only read-heavy, non-secret views are cached (incident detail/list, dashboard stats, member
 *       list), always with a TTL as a safety net.</li>
 *   <li>Writers invalidate <em>after commit</em> ({@link #invalidateIncident}), so a reader cannot re-cache
 *       data from before the change.</li>
 *   <li>Redis is optional: any Redis failure falls through to the database, and after a failure the
 *       cache is bypassed for 30 s so a dead Redis does not slow every request.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCache {

    public static final Duration DETAIL_TTL = Duration.ofMinutes(5);
    public static final Duration LIST_TTL = Duration.ofSeconds(60);
    public static final Duration STATS_TTL = Duration.ofSeconds(60);
    public static final Duration MEMBERS_TTL = Duration.ofMinutes(10);

    private static final String PREFIX = "ip:";
    private static final long BYPASS_MILLIS = 30_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private volatile long bypassUntil = 0;

    public static String key(Long organizationId, String kind, String id) {
        return PREFIX + organizationId + ":" + kind + ":" + id;
    }

    /** Stable, bounded key fragment for free-text filters (the search box). */
    public static String hash(String value) {
        return value == null ? "-" : DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    public <T> T get(Long organizationId, String kind, String id, Duration ttl,
                     TypeReference<T> type, Supplier<T> loader) {
        String key = key(organizationId, kind, id);
        if (available()) {
            try {
                String cached = redis.opsForValue().get(key);
                if (cached != null) {
                    return objectMapper.readValue(cached, type);
                }
            } catch (Exception e) {
                tripBreaker(e);
            }
        }

        T value = loader.get();

        if (value != null && available()) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            } catch (Exception e) {
                tripBreaker(e);
            }
        }
        return value;
    }

    /** After the surrounding transaction commits: drop the incident's detail plus all list/stat views of its organization. */
    public void invalidateIncident(Long organizationId, Long incidentId) {
        TransactionUtils.afterCommit(() -> {
            if (incidentId != null) {
                delete(List.of(key(organizationId, "incident", String.valueOf(incidentId))));
            }
            deleteByPattern(PREFIX + organizationId + ":list:*");
            deleteByPattern(PREFIX + organizationId + ":stats:*");
        });
    }

    public void invalidateMembers(Long organizationId) {
        TransactionUtils.afterCommit(() -> delete(List.of(key(organizationId, "members", "all"))));
    }

    private void delete(List<String> keys) {
        if (!available() || keys.isEmpty()) {
            return;
        }
        try {
            redis.delete(keys);
        } catch (Exception e) {
            tripBreaker(e);
        }
    }

    private void deleteByPattern(String pattern) {
        if (!available()) {
            return;
        }
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(200).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            tripBreaker(e);
        }
    }

    private boolean available() {
        return System.currentTimeMillis() >= bypassUntil;
    }

    private void tripBreaker(Exception e) {
        bypassUntil = System.currentTimeMillis() + BYPASS_MILLIS;
        log.warn("Redis cache unavailable, bypassing it for {}s: {}", BYPASS_MILLIS / 1000, e.getMessage());
    }
}
