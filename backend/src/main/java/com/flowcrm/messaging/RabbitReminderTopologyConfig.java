package com.flowcrm.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitReminderTopologyConfig {

    @Bean
    MessageConverter reminderMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    DirectExchange remindersExchange() {
        return new DirectExchange(ReminderQueues.EXCHANGE, true, false);
    }

    /**
     * Main work queue. Unexpected nacks dead-letter into the retry exchange path.
     */
    @Bean
    Queue remindersQueue() {
        return QueueBuilder.durable(ReminderQueues.MAIN_QUEUE)
                .withArgument("x-dead-letter-exchange", ReminderQueues.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ReminderQueues.ROUTING_RETRY)
                .build();
    }

    /**
     * Delayed retry queue: TTL then dead-letters back to the main routing key.
     */
    @Bean
    Queue remindersRetryQueue(ReminderProperties reminderProperties) {
        return QueueBuilder.durable(ReminderQueues.RETRY_QUEUE)
                .withArgument("x-message-ttl", reminderProperties.getRetryDelayMs())
                .withArgument("x-dead-letter-exchange", ReminderQueues.EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ReminderQueues.ROUTING_SCHEDULED)
                .build();
    }

    @Bean
    Queue remindersDlq() {
        return QueueBuilder.durable(ReminderQueues.DLQ).build();
    }

    @Bean
    Binding remindersBinding(Queue remindersQueue, DirectExchange remindersExchange) {
        return BindingBuilder.bind(remindersQueue).to(remindersExchange).with(ReminderQueues.ROUTING_SCHEDULED);
    }

    @Bean
    Binding remindersRetryBinding(Queue remindersRetryQueue, DirectExchange remindersExchange) {
        return BindingBuilder.bind(remindersRetryQueue).to(remindersExchange).with(ReminderQueues.ROUTING_RETRY);
    }

    @Bean
    Binding remindersDlqBinding(Queue remindersDlq, DirectExchange remindersExchange) {
        return BindingBuilder.bind(remindersDlq).to(remindersExchange).with(ReminderQueues.ROUTING_DLQ);
    }
}
