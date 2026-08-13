package com.flowcrm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.task.Task;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records outbox events inside the caller's transaction.
 * Publishing to RabbitMQ is handled separately by {@link OutboxPublisher}.
 */
@Service
public class OutboxEventRecorder {

    public static final String AGGREGATE_TYPE_TASK = "TASK";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventRecorder(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Marks still-PENDING FOLLOW_UP_SCHEDULED rows for the task as SUPERSEDED.
     * Preserves audit history; does not delete rows.
     */
    @Transactional
    public int supersedePendingFollowUps(UUID taskId) {
        return outboxEventRepository.supersedePending(
                AGGREGATE_TYPE_TASK,
                taskId,
                OutboxEventType.FOLLOW_UP_SCHEDULED,
                OutboxEventStatus.PENDING,
                OutboxEventStatus.SUPERSEDED);
    }

    /**
     * Schedules a follow-up with available_at = reminderAt so the publisher
     * does not emit to RabbitMQ until the reminder is due.
     */
    @Transactional
    public OutboxEvent recordFollowUpScheduled(Task task) {
        if (task.getReminderAt() == null) {
            throw new IllegalArgumentException("Cannot schedule follow-up without reminderAt");
        }

        FollowUpScheduledPayload payload = FollowUpScheduledPayload.from(task);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(AGGREGATE_TYPE_TASK);
        event.setAggregateId(task.getId());
        event.setEventType(OutboxEventType.FOLLOW_UP_SCHEDULED);
        event.setPayload(toJson(payload));
        event.setStatus(OutboxEventStatus.PENDING);
        event.setAvailableAt(task.getReminderAt());

        return outboxEventRepository.save(event);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
