package com.flowcrm.reminder;

import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.outbox.FollowUpScheduledPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulates reminder delivery with structured logging.
 * When app.reminders.fail-delivery=true, throws to exercise retry/DLQ (dev/test only).
 */
@Service
public class LoggingReminderDeliveryService implements ReminderDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderDeliveryService.class);

    private final ReminderProperties reminderProperties;

    public LoggingReminderDeliveryService(ReminderProperties reminderProperties) {
        this.reminderProperties = reminderProperties;
    }

    @Override
    public void deliver(FollowUpScheduledPayload payload) {
        if (reminderProperties.isFailDelivery()) {
            throw new ReminderDeliveryException(
                    "Simulated reminder delivery failure (app.reminders.fail-delivery=true)");
        }

        log.info(
                "Reminder processed taskId={} leadId={} assignedToId={} title=\"{}\" reminderAt={} dueAt={}",
                payload.taskId(),
                payload.leadId(),
                payload.assignedToId(),
                payload.title(),
                payload.reminderAt(),
                payload.dueAt());
    }
}
