package com.incidentpilot.config;

import com.incidentpilot.security.CustomUserDetailsService;
import com.incidentpilot.security.JwtService;
import com.incidentpilot.security.TenantPrincipal;
import com.incidentpilot.service.IncidentEventPublisher;
import com.incidentpilot.service.NotificationService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * Secures the STOMP channel with the same JWT used by the REST API.
 *
 * <ul>
 *   <li><b>CONNECT</b>: requires an {@code Authorization: Bearer ...} STOMP header with a valid,
 *       unexpired token for an existing user whose organization matches the token claim. Browsers
 *       cannot set HTTP headers on a WebSocket handshake, which is why auth happens here.</li>
 *   <li><b>SUBSCRIBE</b>: only {@code /topic/org.{ownOrganizationId}.incidents} and the user's own
 *       {@code /user/queue/notifications} are allowed; the token expiry is re-checked.</li>
 *   <li><b>SEND</b>: always rejected — clients only listen; all changes go through the REST API.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SESSION_TOKEN_EXPIRY = "tokenExpiryMillis";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessagingException("Sending messages is not allowed");
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK etc. need no extra checks
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing Authorization header");
        }

        try {
            Claims claims = jwtService.parseClaims(header.substring(7));
            if (claims.getSubject() == null || !claims.getExpiration().after(new Date())) {
                throw new MessagingException("Token expired");
            }

            TenantPrincipal principal = (TenantPrincipal) userDetailsService.loadUserByUsername(claims.getSubject());
            Number orgClaim = claims.get(JwtService.CLAIM_ORG_ID, Number.class);
            if (orgClaim != null && orgClaim.longValue() != principal.getOrganizationId()) {
                throw new MessagingException("Token organization mismatch");
            }

            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            Map<String, Object> session = accessor.getSessionAttributes();
            if (session != null) {
                session.put(SESSION_TOKEN_EXPIRY, claims.getExpiration().getTime());
            }
        } catch (MessagingException e) {
            throw e;
        } catch (Exception e) {
            log.debug("STOMP CONNECT rejected: {}", e.getMessage());
            throw new MessagingException("Invalid or expired token");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new MessagingException("Not authenticated");
        }

        Map<String, Object> session = accessor.getSessionAttributes();
        Object expiry = session == null ? null : session.get(SESSION_TOKEN_EXPIRY);
        if (expiry instanceof Long millis && millis < System.currentTimeMillis()) {
            throw new MessagingException("Token expired");
        }

        String destination = accessor.getDestination();
        boolean ownOrgTopic = IncidentEventPublisher.orgTopic(principal.getOrganizationId()).equals(destination);
        boolean ownQueue = ("/user" + NotificationService.USER_QUEUE).equals(destination);

        if (!ownOrgTopic && !ownQueue) {
            log.warn("User '{}' denied subscription to '{}'", principal.getUsername(), destination);
            throw new MessagingException("Subscription not allowed");
        }
    }
}
