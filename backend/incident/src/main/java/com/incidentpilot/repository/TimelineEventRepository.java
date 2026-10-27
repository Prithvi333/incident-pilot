package com.incidentpilot.repository;

import com.incidentpilot.model.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    /** Returns timeline events for an incident, oldest first */
    List<TimelineEvent> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
