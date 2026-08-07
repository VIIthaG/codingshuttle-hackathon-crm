package com.flowcrm.reminder;

import com.flowcrm.outbox.FollowUpScheduledPayload;

/**
 * Abstraction for delivering a follow-up reminder.
 * Real email/SMS providers can replace the logging implementation later.
 */
public interface ReminderDeliveryService {

    void deliver(FollowUpScheduledPayload payload);
}
