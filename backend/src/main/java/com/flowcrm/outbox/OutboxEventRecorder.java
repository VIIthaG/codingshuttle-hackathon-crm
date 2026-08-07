package com.flowcrm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records outbox events inside the caller's transaction.
 * No publishing happens in this phase.
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

    @Transactional
    public OutboxEvent recordFollowUpScheduled(Task task) {
        if (task.getReminderAt() == null) {
            throw new IllegalArgumentException("Cannot schedule follow-up without reminderAt");
        }

        FollowUpScheduledPayload payload = new FollowUpScheduledPayload(
                task.getId(),
                task.getLead().getId(),
                task.getAssignedTo().getId(),
                task.getTitle(),
                task.getReminderAt(),
                task.getDueAt());

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(AGGREGATE_TYPE_TASK);
        event.setAggregateId(task.getId());
        event.setEventType(OutboxEventType.FOLLOW_UP_SCHEDULED);
        event.setPayload(toJson(payload));
        event.setStatus(OutboxEventStatus.PENDING);

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
