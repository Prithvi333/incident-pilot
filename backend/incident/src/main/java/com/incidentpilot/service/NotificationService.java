package com.incidentpilot.service;

import com.incidentpilot.dto.NotificationResponse;
import com.incidentpilot.exception.ResourceNotFoundException;
import com.incidentpilot.model.Incident;
import com.incidentpilot.model.Notification;
import com.incidentpilot.model.NotificationType;
import com.incidentpilot.model.User;
import com.incidentpilot.repository.NotificationRepository;
import com.incidentpilot.security.CurrentUser;
import com.incidentpilot.security.TenantPrincipal;
import com.incidentpilot.util.TransactionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates, stores and delivers per-user notifications.
 *
 * <p>Rules enforced here: the actor never notifies themselves, recipients must belong to the same
 * organization as the incident, and a (recipient, event) pair is stored at most once. Persisted in
 * the caller's transaction; the WebSocket push happens only after that transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String USER_QUEUE = "/queue/notifications";
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private record Push(String username, NotificationResponse payload) {
    }

    @Transactional
    public void notifyUsers(Incident incident, NotificationType type, String actor,
                            Collection<User> recipients, String title, String message, String discriminator) {
        Long orgId = incident.getOrganization().getId();
        String dedupeKey = type.name() + ":" + incident.getId() + ":" + discriminator;
        String safeMessage = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH - 1) + "…" : message;

        Set<Long> seen = new HashSet<>();
        List<Push> pushes = new ArrayList<>();

        for (User recipient : recipients) {
            if (recipient == null || !seen.add(recipient.getId())) {
                continue;
            }
            boolean sameOrg = recipient.getOrganization() != null
                    && orgId.equals(recipient.getOrganization().getId());
            boolean isActor = recipient.getUsername().equals(actor);
            if (!sameOrg || isActor
                    || notificationRepository.existsByRecipientIdAndDedupeKey(recipient.getId(), dedupeKey)) {
                continue;
            }

            Notification saved = notificationRepository.save(Notification.builder()
                    .organizationId(orgId)
                    .recipientId(recipient.getId())
                    .incidentId(incident.getId())
                    .type(type)
                    .title(title)
                    .message(safeMessage)
                    .actor(actor)
                    .dedupeKey(dedupeKey)
                    .build());
            pushes.add(new Push(recipient.getUsername(), NotificationResponse.from(saved)));
        }

        if (!pushes.isEmpty()) {
            TransactionUtils.afterCommit(() -> pushes.forEach(p ->
                    messagingTemplate.convertAndSendToUser(p.username(), USER_QUEUE, p.payload())));
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(boolean unreadOnly, int limit) {
        TenantPrincipal me = CurrentUser.require();
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE_SIZE)));
        List<Notification> items = unreadOnly
                ? notificationRepository.findByRecipientIdAndOrganizationIdAndReadFalseOrderByCreatedAtDescIdDesc(
                        me.getUserId(), me.getOrganizationId(), page)
                : notificationRepository.findByRecipientIdAndOrganizationIdOrderByCreatedAtDescIdDesc(
                        me.getUserId(), me.getOrganizationId(), page);
        return items.stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        TenantPrincipal me = CurrentUser.require();
        return notificationRepository.countByRecipientIdAndOrganizationIdAndReadFalse(
                me.getUserId(), me.getOrganizationId());
    }

    /** Marks one notification read. Someone else's (or another tenant's) id resolves to 404. */
    @Transactional
    public NotificationResponse markRead(Long id) {
        TenantPrincipal me = CurrentUser.require();
        Notification n = notificationRepository
                .findByIdAndRecipientIdAndOrganizationId(id, me.getUserId(), me.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
        }
        return NotificationResponse.from(n);
    }

    @Transactional
    public int markAllRead() {
        TenantPrincipal me = CurrentUser.require();
        return notificationRepository.markAllRead(me.getUserId(), me.getOrganizationId(), LocalDateTime.now());
    }
}
