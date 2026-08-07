package com.flowcrm.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload for FOLLOW_UP_SCHEDULED outbox events.
 * Enough for a future reminder consumer without inferring intent.
 */
public record FollowUpScheduledPayload(
        UUID taskId,
        UUID leadId,
        UUID assignedToId,
        String title,
        Instant reminderAt,
        Instant dueAt) {
}
