package com.incidentpilot.repository;

import com.incidentpilot.model.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    /** Returns timeline events for an incident, oldest first */
    List<TimelineEvent> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    /** Human authors who commented on / acted on the incident (used to notify participants). */
    @Query("SELECT DISTINCT t.author FROM TimelineEvent t "
            + "WHERE t.incident.id = :incidentId AND t.type <> com.incidentpilot.model.TimelineEventType.STATUS_CHANGE")
    List<String> findParticipantUsernames(@Param("incidentId") Long incidentId);
}
