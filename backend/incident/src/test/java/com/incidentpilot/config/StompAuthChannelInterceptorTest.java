package com.incidentpilot.config;

import com.incidentpilot.TestSecurity;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.security.CustomUserDetailsService;
import com.incidentpilot.security.JwtService;
import com.incidentpilot.security.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** WebSocket security: who may connect, subscribe and send. */
class StompAuthChannelInterceptorTest {

    private final Organization acme = TestSecurity.org(1);
    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;
    private TenantPrincipal alice;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", Base64.getEncoder().encodeToString(key));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);

        alice = new TenantPrincipal(TestSecurity.user(10, "alice", Role.ENGINEER, acme));
        CustomUserDetailsService users = mock(CustomUserDetailsService.class);
        when(users.loadUserByUsername(anyString())).thenReturn(alice);
        interceptor = new StompAuthChannelInterceptor(jwtService, users);
    }

    private Message<byte[]> connect(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination, long tokenExpiryMillis) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities()));
        HashMap<String, Object> session = new HashMap<>();
        session.put("tokenExpiryMillis", tokenExpiryMillis);
        accessor.setSessionAttributes(session);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination) {
        return subscribe(destination, System.currentTimeMillis() + 60_000);
    }

    @Test
    void connectWithValidTokenAuthenticatesTheUser() {
        String token = jwtService.generateToken("alice", "ENGINEER", 1L);
        Message<byte[]> message = connect("Bearer " + token);

        interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("alice");
    }

    @Test
    void connectWithoutTokenOrWithGarbageIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null)).isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer garbage"), null)).isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithTokenForAnotherOrganizationThanTheUserIsRejected() {
        // token claims org 2 but alice belongs to org 1
        String forged = jwtService.generateToken("alice", "ENGINEER", 2L);

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + forged), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L);
        String expired = jwtService.generateToken("alice", "ENGINEER", 1L);

        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + expired), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void userMaySubscribeToOwnOrganizationTopicAndOwnQueueOnly() {
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/org.1.incidents"), null)).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(subscribe("/user/queue/notifications"), null)).doesNotThrowAnyException();

        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/org.2.incidents"), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/org.1.incidents.extra"), null))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/anything"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribingWithAnExpiredSessionTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/org.1.incidents", System.currentTimeMillis() - 1), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void clientsCannotSendMessages() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/anything");
        accessor.setLeaveMutable(true);
        Message<byte[]> send = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(send, null)).isInstanceOf(MessagingException.class);
    }
}
