package com.flowcrm.messaging;

/**
 * RabbitMQ topology names for reminder messaging.
 */
public final class ReminderQueues {

    public static final String EXCHANGE = "flowcrm.reminders.exchange";

    public static final String MAIN_QUEUE = "flowcrm.reminders.queue";
    public static final String RETRY_QUEUE = "flowcrm.reminders.retry.queue";
    public static final String DLQ = "flowcrm.reminders.dlq";

    public static final String ROUTING_SCHEDULED = "reminder.scheduled";
    public static final String ROUTING_RETRY = "reminder.retry";
    public static final String ROUTING_DLQ = "reminder.dlq";

    public static final String HEADER_ATTEMPT = "x-attempt";

    private ReminderQueues() {
    }
}
