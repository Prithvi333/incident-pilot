package com.incidentpilot.service;

import com.incidentpilot.TestSecurity;
import com.incidentpilot.exception.ResourceNotFoundException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Notification;
import com.incidentpilot.model.NotificationType;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.Severity;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recipient filtering, duplicate prevention and per-user access of {@link NotificationService}. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks NotificationService notificationService;

    private final Organization acme = TestSecurity.org(1);
    private final Organization globex = TestSecurity.org(2);

    @AfterEach
    void clearContext() {
        TestSecurity.clear();
    }

    @Test
    void actorOtherTenantsAndAlreadyNotifiedUsersAreSkipped() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P2_HIGH);
        User actor = TestSecurity.user(1, "alice", Role.ENGINEER, acme);
        User bob = TestSecurity.user(2, "bob", Role.ENGINEER, acme);
        User carol = TestSecurity.user(3, "carol", Role.ENGINEER, acme);
        User eve = TestSecurity.user(4, "eve", Role.ENGINEER, globex); // different organization

        when(notificationRepository.existsByRecipientIdAndDedupeKey(anyLong(), anyString())).thenReturn(false);
        when(notificationRepository.existsByRecipientIdAndDedupeKey(3L, "COMMENT_ADDED:5:c1")).thenReturn(true);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });

        notificationService.notifyUsers(incident, NotificationType.COMMENT_ADDED, "alice",
                List.of(actor, bob, carol, eve), "title", "message", "c1");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());   // only bob
        assertThat(saved.getValue().getRecipientId()).isEqualTo(2L);
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(1L);
        assertThat(saved.getValue().getDedupeKey()).isEqualTo("COMMENT_ADDED:5:c1");
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/notifications"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("eve"), anyString(), any());
    }

    @Test
    void sameUserListedTwiceIsNotifiedOnce() {
        Incident incident = TestSecurity.incident(5, acme, IncidentStatus.TRIGGERED, Severity.P2_HIGH);
        User bob = TestSecurity.user(2, "bob", Role.ENGINEER, acme);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notifyUsers(incident, NotificationType.STATUS_CHANGED, "system",
                List.of(bob, bob), "t", "m", "v1");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markingSomeoneElsesNotificationReadIsNotFound() {
        TestSecurity.login(TestSecurity.user(1, "alice", Role.ENGINEER, acme));
        when(notificationRepository.findByIdAndRecipientIdAndOrganizationId(9L, 1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markReadSetsReadFlag() {
        TestSecurity.login(TestSecurity.user(1, "alice", Role.ENGINEER, acme));
        Notification n = Notification.builder().id(9L).recipientId(1L).organizationId(1L)
                .type(NotificationType.STATUS_CHANGED).title("t").message("m").dedupeKey("k").build();
        when(notificationRepository.findByIdAndRecipientIdAndOrganizationId(9L, 1L, 1L)).thenReturn(Optional.of(n));

        assertThat(notificationService.markRead(9L).isRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
    }
}
