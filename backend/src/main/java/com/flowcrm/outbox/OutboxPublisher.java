package com.flowcrm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.lock.DistributedLock;
import com.flowcrm.lock.OutboxLockProperties;
import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.messaging.ReminderQueues;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls due PENDING outbox rows and publishes them to RabbitMQ.
 *
 * <p>Postgres and RabbitMQ are not one distributed transaction. After a successful
 * broker publish we mark PUBLISHED. If the process crashes between broker ACK and
 * the DB update, a later poll may republish — consumers must be idempotent.
 *
 * <p>Future reminders (available_at in the future) stay PENDING and are not
 * published early. The retry queue is not used for scheduling delays.
 *
 * <p>Scheduled polls are guarded by a Redis distributed lock so multiple app
 * instances do not publish the same batch concurrently. Lock failure skips the cycle.
 */
@Component
@ConditionalOnProperty(
        name = {"app.messaging.enabled", "app.outbox.publisher.enabled"},
        havingValue = "true",
        matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxPublisherProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLock distributedLock;
    private final OutboxLockProperties lockProperties;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            OutboxPublisherProperties properties,
            TransactionTemplate transactionTemplate,
            DistributedLock distributedLock,
            OutboxLockProperties lockProperties) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.distributedLock = distributedLock;
        this.lockProperties = lockProperties;
    }

    @Scheduled(fixedDelayString = "${app.outbox.publisher.poll-interval-ms:2000}")
    public void publishPendingBatch() {
        String lockKey = lockProperties.getKey();
        Duration ttl = Duration.ofSeconds(lockProperties.getTtlSeconds());
        Optional<String> token = distributedLock.tryAcquire(lockKey, ttl);
        if (token.isEmpty()) {
            log.debug("Skipping outbox poll; distributed lock not acquired key={}", lockKey);
            return;
        }
        try {
            publishDueEvents();
        } finally {
            distributedLock.release(lockKey, token.get());
        }
    }

    private void publishDueEvents() {
        Instant now = Instant.now();
        List<OutboxEvent> due = outboxEventRepository.findDueByStatus(
                OutboxEventStatus.PENDING, now, PageRequest.of(0, properties.getBatchSize()));

        for (OutboxEvent event : due) {
            publishOne(event.getId());
        }
    }

    /**
     * Publishes a single due PENDING event. Marks PUBLISHED only after broker success.
     * On broker failure the row stays PENDING for a future poll.
     * Not-yet-due events are left PENDING and not sent to RabbitMQ.
     */
    public boolean publishOne(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.PENDING) {
            return false;
        }
        if (event.getAvailableAt() != null && event.getAvailableAt().isAfter(Instant.now())) {
            log.debug(
                    "Outbox event id={} not due yet availableAt={}; leaving PENDING",
                    event.getId(),
                    event.getAvailableAt());
            return false;
        }
        if (event.getEventType() != OutboxEventType.FOLLOW_UP_SCHEDULED) {
            log.warn("Skipping unsupported outbox event type {} id={}", event.getEventType(), event.getId());
            return false;
        }

        try {
            FollowUpScheduledPayload payload =
                    objectMapper.readValue(event.getPayload(), FollowUpScheduledPayload.class);
            ReminderMessage message = new ReminderMessage(event.getId(), payload);

            rabbitTemplate.convertAndSend(
                    ReminderQueues.EXCHANGE, ReminderQueues.ROUTING_SCHEDULED, message);

            markPublished(event.getId());
            log.info("Published outbox event id={} type={}", event.getId(), event.getEventType());
            return true;
        } catch (JsonProcessingException ex) {
            log.error("Invalid outbox payload for event id={}; leaving PENDING", event.getId(), ex);
            return false;
        } catch (RuntimeException ex) {
            log.warn("Failed to publish outbox event id={}; leaving PENDING", event.getId(), ex);
            return false;
        }
    }

    private void markPublished(UUID eventId) {
        transactionTemplate.executeWithoutResult(status -> {
            OutboxEvent event = outboxEventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalStateException("Outbox event missing: " + eventId));
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            outboxEventRepository.save(event);
        });
    }
}
