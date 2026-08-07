package com.flowcrm.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateIdAndEventType(UUID aggregateId, OutboxEventType eventType);

    long countByEventTypeAndStatus(OutboxEventType eventType, OutboxEventStatus status);

    /**
     * Due PENDING events only — available_at must already be reached.
     * Future reminders stay PENDING until their available_at.
     */
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = :status
              AND e.availableAt <= :now
            ORDER BY e.availableAt ASC, e.createdAt ASC
            """)
    List<OutboxEvent> findDueByStatus(
            @Param("status") OutboxEventStatus status,
            @Param("now") Instant now,
            Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE OutboxEvent e
            SET e.status = :superseded
            WHERE e.aggregateType = :aggregateType
              AND e.aggregateId = :aggregateId
              AND e.eventType = :eventType
              AND e.status = :pending
            """)
    int supersedePending(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") OutboxEventType eventType,
            @Param("pending") OutboxEventStatus pending,
            @Param("superseded") OutboxEventStatus superseded);
}
