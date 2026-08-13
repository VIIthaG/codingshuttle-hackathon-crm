package com.flowcrm.outbox;

import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.task.Task;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for FOLLOW_UP_SCHEDULED outbox events.
 * Enough for a future reminder consumer without inferring intent.
 *
 * <p>{@code leadId} remains for backward compatibility with in-flight messages.
 * Non-lead tasks set {@code leadId} to null and populate relatedType/relatedId/relatedName.
 */
public record FollowUpScheduledPayload(
        UUID taskId,
        UUID leadId,
        UUID assignedToId,
        String title,
        Instant reminderAt,
        Instant dueAt,
        RelatedRecordType relatedType,
        UUID relatedId,
        String relatedName) {

    public FollowUpScheduledPayload(
            UUID taskId,
            UUID leadId,
            UUID assignedToId,
            String title,
            Instant reminderAt,
            Instant dueAt) {
        this(
                taskId,
                leadId,
                assignedToId,
                title,
                reminderAt,
                dueAt,
                leadId != null ? RelatedRecordType.LEAD : null,
                leadId,
                null);
    }

    public FollowUpScheduledPayload {
        if (relatedType == null && leadId != null) {
            relatedType = RelatedRecordType.LEAD;
        }
        if (relatedId == null && leadId != null) {
            relatedId = leadId;
        }
    }

    public static FollowUpScheduledPayload from(Task task) {
        RelatedRecordType type = task.relatedType();
        return new FollowUpScheduledPayload(
                task.getId(),
                type == RelatedRecordType.LEAD ? task.getLead().getId() : null,
                task.getAssignedTo().getId(),
                task.getTitle(),
                task.getReminderAt(),
                task.getDueAt(),
                type,
                task.relatedId(),
                task.relatedName());
    }
}
