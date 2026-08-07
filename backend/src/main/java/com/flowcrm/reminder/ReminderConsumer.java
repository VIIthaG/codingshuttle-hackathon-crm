package com.flowcrm.reminder;

import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.messaging.ReminderQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes FOLLOW_UP_SCHEDULED messages.
 *
 * <p>On failure: republish to the retry routing key (TTL delay then back to main),
 * or to the DLQ after max attempts. Exceptions are swallowed after routing so
 * Spring does not also dead-letter via the queue DLX.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReminderConsumer.class);

    private final ReminderProcessingService reminderProcessingService;
    private final RabbitTemplate rabbitTemplate;
    private final ReminderProperties reminderProperties;

    public ReminderConsumer(
            ReminderProcessingService reminderProcessingService,
            RabbitTemplate rabbitTemplate,
            ReminderProperties reminderProperties) {
        this.reminderProcessingService = reminderProcessingService;
        this.rabbitTemplate = rabbitTemplate;
        this.reminderProperties = reminderProperties;
    }

    @RabbitListener(queues = ReminderQueues.MAIN_QUEUE)
    public void onReminder(
            ReminderMessage message,
            @Header(name = ReminderQueues.HEADER_ATTEMPT, required = false) Integer attemptHeader) {
        int attempt = attemptHeader == null ? 1 : attemptHeader;
        try {
            reminderProcessingService.process(message);
        } catch (RuntimeException ex) {
            handleFailure(message, attempt, ex);
        }
    }

    /**
     * Visible for tests — applies retry/DLQ routing without requiring a live broker listener.
     */
    public void handleFailure(ReminderMessage message, int attempt, RuntimeException cause) {
        if (attempt >= reminderProperties.getMaxAttempts()) {
            log.error(
                    "Reminder processing exhausted retries eventId={} attempt={}; routing to DLQ",
                    message.eventId(),
                    attempt,
                    cause);
            rabbitTemplate.convertAndSend(
                    ReminderQueues.EXCHANGE,
                    ReminderQueues.ROUTING_DLQ,
                    message,
                    m -> {
                        m.getMessageProperties().setHeader(ReminderQueues.HEADER_ATTEMPT, attempt);
                        m.getMessageProperties().setHeader(AmqpHeaders.MESSAGE_ID, message.eventId().toString());
                        return m;
                    });
            return;
        }

        int nextAttempt = attempt + 1;
        log.warn(
                "Reminder processing failed eventId={} attempt={}; routing to retry (nextAttempt={})",
                message.eventId(),
                attempt,
                nextAttempt,
                cause);
        rabbitTemplate.convertAndSend(
                ReminderQueues.EXCHANGE,
                ReminderQueues.ROUTING_RETRY,
                message,
                m -> {
                    m.getMessageProperties().setHeader(ReminderQueues.HEADER_ATTEMPT, nextAttempt);
                    m.getMessageProperties().setHeader(AmqpHeaders.MESSAGE_ID, message.eventId().toString());
                    return m;
                });
    }
}
