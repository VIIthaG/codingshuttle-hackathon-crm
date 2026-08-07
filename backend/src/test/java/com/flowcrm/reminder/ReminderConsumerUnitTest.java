package com.flowcrm.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.messaging.ReminderQueues;
import com.flowcrm.outbox.FollowUpScheduledPayload;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ReminderConsumerUnitTest {

    @Mock
    private ReminderProcessingService reminderProcessingService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ReminderProperties properties;
    private ReminderConsumer consumer;

    @BeforeEach
    void setUp() {
        properties = new ReminderProperties();
        properties.setMaxAttempts(3);
        consumer = new ReminderConsumer(reminderProcessingService, rabbitTemplate, properties);
    }

    @Test
    void onReminder_staleOrDuplicate_doesNotEnterRetryOrDlq() {
        ReminderMessage message = sampleMessage();
        when(reminderProcessingService.process(message)).thenReturn(false);

        consumer.onReminder(message, 1);

        verify(reminderProcessingService).process(message);
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void onReminder_failureBeforeMax_routesToRetry() {
        ReminderMessage message = sampleMessage();
        when(reminderProcessingService.process(message)).thenThrow(new ReminderDeliveryException("boom"));

        consumer.onReminder(message, 1);

        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ReminderQueues.EXCHANGE),
                org.mockito.ArgumentMatchers.eq(ReminderQueues.ROUTING_RETRY),
                org.mockito.ArgumentMatchers.eq(message),
                processorCaptor.capture());
    }

    @Test
    void onReminder_failureAtMax_routesToDlq() {
        ReminderMessage message = sampleMessage();
        when(reminderProcessingService.process(message)).thenThrow(new ReminderDeliveryException("boom"));

        consumer.onReminder(message, 3);

        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ReminderQueues.EXCHANGE),
                org.mockito.ArgumentMatchers.eq(ReminderQueues.ROUTING_DLQ),
                org.mockito.ArgumentMatchers.eq(message),
                any(MessagePostProcessor.class));
    }

    @Test
    void handleFailure_incrementsThroughRetriesThenDlq() {
        ReminderMessage message = sampleMessage();

        consumer.handleFailure(message, 1, new ReminderDeliveryException("1"));
        consumer.handleFailure(message, 2, new ReminderDeliveryException("2"));
        consumer.handleFailure(message, 3, new ReminderDeliveryException("3"));

        verify(rabbitTemplate, times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ReminderQueues.EXCHANGE),
                org.mockito.ArgumentMatchers.eq(ReminderQueues.ROUTING_RETRY),
                org.mockito.ArgumentMatchers.eq(message),
                any(MessagePostProcessor.class));
        verify(rabbitTemplate, times(1)).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ReminderQueues.EXCHANGE),
                org.mockito.ArgumentMatchers.eq(ReminderQueues.ROUTING_DLQ),
                org.mockito.ArgumentMatchers.eq(message),
                any(MessagePostProcessor.class));
    }

    private ReminderMessage sampleMessage() {
        FollowUpScheduledPayload payload = new FollowUpScheduledPayload(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Follow up",
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-11T10:00:00Z"));
        return new ReminderMessage(UUID.randomUUID(), payload);
    }
}
