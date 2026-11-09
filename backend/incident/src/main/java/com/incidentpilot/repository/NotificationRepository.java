package com.incidentpilot.repository;

import com.incidentpilot.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** All reads are filtered by recipient AND organization, so users only ever see their own notifications. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndOrganizationIdOrderByCreatedAtDescIdDesc(
            Long recipientId, Long organizationId, Pageable pageable);

    List<Notification> findByRecipientIdAndOrganizationIdAndReadFalseOrderByCreatedAtDescIdDesc(
            Long recipientId, Long organizationId, Pageable pageable);

    long countByRecipientIdAndOrganizationIdAndReadFalse(Long recipientId, Long organizationId);

    Optional<Notification> findByIdAndRecipientIdAndOrganizationId(Long id, Long recipientId, Long organizationId);

    boolean existsByRecipientIdAndDedupeKey(Long recipientId, String dedupeKey);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now "
            + "WHERE n.recipientId = :recipientId AND n.organizationId = :orgId AND n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId,
                    @Param("orgId") Long organizationId,
                    @Param("now") LocalDateTime now);
}
