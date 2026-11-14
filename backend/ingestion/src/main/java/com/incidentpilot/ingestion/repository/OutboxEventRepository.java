package com.incidentpilot.ingestion.repository;

import com.incidentpilot.ingestion.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** Small batch: document events carry the full extracted text. */
    List<OutboxEvent> findTop20ByStatusOrderByIdAsc(OutboxEvent.Status status);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = com.incidentpilot.ingestion.model.OutboxEvent.Status.SENT AND o.sentAt < :before")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
