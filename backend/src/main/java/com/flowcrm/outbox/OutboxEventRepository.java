package com.flowcrm.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByAggregateIdAndEventType(UUID aggregateId, OutboxEventType eventType);

    long countByEventTypeAndStatus(OutboxEventType eventType, OutboxEventStatus status);
}
