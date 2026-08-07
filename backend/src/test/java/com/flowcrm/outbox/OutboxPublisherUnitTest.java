package com.flowcrm.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.lock.DistributedLock;
import com.flowcrm.lock.OutboxLockProperties;
import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.messaging.ReminderQueues;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherUnitTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private DistributedLock distributedLock;

    private ObjectMapper objectMapper;
    private OutboxPublisher publisher;
    private OutboxLockProperties lockProperties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        OutboxPublisherProperties properties = new OutboxPublisherProperties();
        lockProperties = new OutboxLockProperties();
        TransactionTemplate transactionTemplate = new TransactionTemplate(new NoOpTransactionManager());
        publisher = new OutboxPublisher(
                outboxEventRepository,
                rabbitTemplate,
                objectMapper,
                properties,
                transactionTemplate,
                distributedLock,
                lockProperties);
    }

    @Test
    void publishOne_dueEvent_marksPublished() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = pendingEvent(eventId, Instant.now().minus(1, ChronoUnit.MINUTES));
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        boolean published = publisher.publishOne(eventId);

        assertThat(published).isTrue();
        ArgumentCaptor<ReminderMessage> messageCaptor = ArgumentCaptor.forClass(ReminderMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(ReminderQueues.EXCHANGE),
                eq(ReminderQueues.ROUTING_SCHEDULED),
                messageCaptor.capture());
        assertThat(messageCaptor.getValue().eventId()).isEqualTo(eventId);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    void publishOne_futureAvailableAt_leavesPending() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = pendingEvent(eventId, Instant.now().plus(2, ChronoUnit.DAYS));
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        boolean published = publisher.publishOne(eventId);

        assertThat(published).isFalse();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void publishPendingBatch_queriesOnlyDueEvents_whenLockAcquired() {
        when(distributedLock.tryAcquire(eq(lockProperties.getKey()), any(Duration.class)))
                .thenReturn(Optional.of("token-1"));
        when(outboxEventRepository.findDueByStatus(
                        eq(OutboxEventStatus.PENDING), any(Instant.class), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of());

        publisher.publishPendingBatch();

        verify(outboxEventRepository)
                .findDueByStatus(eq(OutboxEventStatus.PENDING), any(Instant.class), eq(PageRequest.of(0, 50)));
        verify(distributedLock).release(lockProperties.getKey(), "token-1");
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publishPendingBatch_skipsWhenLockUnavailable() {
        when(distributedLock.tryAcquire(eq(lockProperties.getKey()), any(Duration.class)))
                .thenReturn(Optional.empty());

        publisher.publishPendingBatch();

        verify(outboxEventRepository, never()).findDueByStatus(any(), any(), any());
        verify(distributedLock, never()).release(anyString(), anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publishOne_brokerFailure_leavesPending() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = pendingEvent(eventId, Instant.now().minusSeconds(10));
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        boolean published = publisher.publishOne(eventId);

        assertThat(published).isFalse();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).save(any());
    }

    private OutboxEvent pendingEvent(UUID eventId, Instant availableAt) {
        try {
            FollowUpScheduledPayload payload = new FollowUpScheduledPayload(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Call",
                    availableAt,
                    Instant.parse("2026-08-11T12:00:00Z"));
            OutboxEvent event = new OutboxEvent();
            event.setId(eventId);
            event.setAggregateType(OutboxEventRecorder.AGGREGATE_TYPE_TASK);
            event.setAggregateId(payload.taskId());
            event.setEventType(OutboxEventType.FOLLOW_UP_SCHEDULED);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus(OutboxEventStatus.PENDING);
            event.setAvailableAt(availableAt);
            return event;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
