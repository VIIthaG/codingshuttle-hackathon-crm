package com.flowcrm.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.enums.LeadSource;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.Lead;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.messaging.ReminderProperties;
import com.flowcrm.outbox.FollowUpScheduledPayload;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.task.Task;
import com.flowcrm.call.CallRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class ReminderProcessingServiceTest {

    @Autowired
    private ReminderProcessingService reminderProcessingService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private ReminderProperties reminderProperties;

    @MockitoSpyBean
    private ReminderDeliveryService reminderDeliveryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private CallRepository callRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void clean() {
        reminderProperties.setFailDelivery(false);
        processedMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        callRepository.deleteAll();
        meetingRepository.deleteAll();
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        dealRepository.deleteAll();
        contactRepository.deleteAll();
        accountRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void process_deliversAndRecordsOnce() {
        Fixture fixture = persistOpenTask(Instant.parse("2026-08-10T10:00:00Z"));
        ReminderMessage message = messageFor(fixture);

        assertThat(reminderProcessingService.process(message)).isTrue();
        assertThat(processedMessageRepository.existsById(message.eventId())).isTrue();
        verify(reminderDeliveryService, times(1)).deliver(message.payload());

        assertThat(reminderProcessingService.process(message)).isFalse();
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        verify(reminderDeliveryService, times(1)).deliver(any());
    }

    @Test
    void process_whenDeliveryFails_doesNotRecordProcessed() {
        reminderProperties.setFailDelivery(true);
        Fixture fixture = persistOpenTask(Instant.parse("2026-08-10T10:00:00Z"));
        ReminderMessage message = messageFor(fixture);

        assertThatThrownBy(() -> reminderProcessingService.process(message))
                .isInstanceOf(ReminderDeliveryException.class);

        assertThat(processedMessageRepository.existsById(message.eventId())).isFalse();
    }

    @Test
    void process_staleWhenReminderAtMismatch_skipsWithoutDelivery() {
        Instant original = Instant.parse("2026-08-10T10:00:00Z");
        Instant rescheduled = Instant.parse("2026-08-10T11:30:00Z");
        Fixture fixture = persistOpenTask(rescheduled);

        ReminderMessage stale = new ReminderMessage(
                UUID.randomUUID(),
                new FollowUpScheduledPayload(
                        fixture.task().getId(),
                        fixture.lead().getId(),
                        fixture.user().getId(),
                        fixture.task().getTitle(),
                        original,
                        fixture.task().getDueAt()));

        assertThat(reminderProcessingService.process(stale)).isFalse();
        assertThat(processedMessageRepository.existsById(stale.eventId())).isTrue();
        verify(reminderDeliveryService, never()).deliver(any());
    }

    @Test
    void process_staleWhenTaskCompleted_skipsWithoutDelivery() {
        Fixture fixture = persistOpenTask(Instant.parse("2026-08-10T10:00:00Z"));
        fixture.task().setStatus(TaskStatus.COMPLETED);
        taskRepository.save(fixture.task());

        ReminderMessage message = messageFor(fixture);
        assertThat(reminderProcessingService.process(message)).isFalse();
        assertThat(processedMessageRepository.existsById(message.eventId())).isTrue();
        verify(reminderDeliveryService, never()).deliver(any());
    }

    private ReminderMessage messageFor(Fixture fixture) {
        return new ReminderMessage(
                UUID.randomUUID(),
                new FollowUpScheduledPayload(
                        fixture.task().getId(),
                        fixture.lead().getId(),
                        fixture.user().getId(),
                        fixture.task().getTitle(),
                        fixture.task().getReminderAt(),
                        fixture.task().getDueAt()));
    }

    private Fixture persistOpenTask(Instant reminderAt) {
        User user = new User();
        user.setEmail("reminder-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Reminder User");
        user.setRole(Role.ADMIN);
        user = userRepository.save(user);

        Lead lead = new Lead();
        lead.setFullName("Reminder Lead");
        lead.setSource(LeadSource.WEB);
        lead.setStatus(LeadStatus.NEW);
        lead.setAssignedTo(user);
        lead = leadRepository.save(lead);

        Task task = new Task();
        task.setLead(lead);
        task.setAssignedTo(user);
        task.setTitle("Follow up");
        task.setDueAt(reminderAt.plus(1, ChronoUnit.DAYS));
        task.setReminderAt(reminderAt);
        task.setStatus(TaskStatus.OPEN);
        task = taskRepository.save(task);

        return new Fixture(user, lead, task);
    }

    private record Fixture(User user, Lead lead, Task task) {
    }
}
