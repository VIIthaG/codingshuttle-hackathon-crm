package com.flowcrm.messaging;

import com.flowcrm.outbox.FollowUpScheduledPayload;
import java.util.UUID;

/**
 * Broker message body for FOLLOW_UP_SCHEDULED.
 * eventId is the outbox_events.id used for consumer idempotency.
 *
 * <p>Note: Postgres outbox commit and RabbitMQ publish are not one distributed
 * transaction — duplicate delivery is possible; consumers must be idempotent.
 */
public record ReminderMessage(
        UUID eventId,
        FollowUpScheduledPayload payload) {
}
