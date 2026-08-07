package com.flowcrm.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private com.flowcrm.reminder.ProcessedMessageRepository processedMessageRepository;

    @MockitoSpyBean
    private OutboxEventRecorder outboxEventRecorder;

    @BeforeEach
    void cleanDatabase() {
        processedMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createTaskWithReminder_writesPendingFollowUpScheduledEvent() throws Exception {
        String token = register("outbox.with@example.com", "Outbox With");
        String leadId = createLead(token, "Outbox Lead");

        Instant dueAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Call back",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode taskJson = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID taskId = UUID.fromString(taskJson.get("id").asText());
        UUID assignedToId = UUID.fromString(taskJson.get("assignedToId").asText());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(
                taskId, OutboxEventType.FOLLOW_UP_SCHEDULED);

        assertThat(events).hasSize(1);
        OutboxEvent event = events.getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getEventType()).isEqualTo(OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(event.getAggregateType()).isEqualTo(OutboxEventRecorder.AGGREGATE_TYPE_TASK);
        assertThat(event.getAggregateId()).isEqualTo(taskId);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAvailableAt()).isEqualTo(reminderAt);

        FollowUpScheduledPayload payload =
                objectMapper.readValue(event.getPayload(), FollowUpScheduledPayload.class);
        assertThat(payload.taskId()).isEqualTo(taskId);
        assertThat(payload.leadId()).isEqualTo(UUID.fromString(leadId));
        assertThat(payload.assignedToId()).isEqualTo(assignedToId);
        assertThat(payload.title()).isEqualTo("Call back");
        assertThat(payload.reminderAt()).isEqualTo(reminderAt);
        assertThat(payload.dueAt()).isEqualTo(dueAt);

        assertThat(outboxEventRepository.findDueByStatus(
                        OutboxEventStatus.PENDING, Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50)))
                .isEmpty();
    }

    @Test
    void createTaskWithoutReminder_writesNoFollowUpEvent() throws Exception {
        String token = register("outbox.none@example.com", "Outbox None");
        String leadId = createLead(token, "No Reminder Lead");

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "No reminder",
                                  "dueAt": "%s"
                                }
                                """.formatted(leadId, Instant.now().plus(2, ChronoUnit.DAYS))))
                .andExpect(status().isCreated());

        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.countByEventTypeAndStatus(
                        OutboxEventType.FOLLOW_UP_SCHEDULED, OutboxEventStatus.PENDING))
                .isZero();
    }

    @Test
    void whenOutboxWriteFails_taskCreateIsRolledBack() throws Exception {
        String token = register("outbox.rollback@example.com", "Outbox Rollback");
        String leadId = createLead(token, "Rollback Lead");

        doThrow(new RuntimeException("simulated outbox failure"))
                .when(outboxEventRecorder)
                .recordFollowUpScheduled(any(Task.class));

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Should roll back",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(
                                        leadId,
                                        Instant.now().plus(2, ChronoUnit.DAYS),
                                        Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().is5xxServerError());

        // Same @Transactional boundary on TaskService.create: task insert + outbox insert roll back together.
        assertThat(taskRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void updateReminderAt_supersedesPreviousAndCreatesNew_unrelatedUpdateDoesNot() throws Exception {
        String token = register("outbox.reschedule@example.com", "Outbox Reschedule");
        String leadId = createLead(token, "Reschedule Lead");

        Instant dueAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Reschedule me",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String taskId = created.get("id").asText();
        String assignedToId = created.get("assignedToId").asText();
        assertThat(outboxEventRepository.findByAggregateIdAndEventType(
                        UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED))
                .hasSize(1);

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Reschedule me - title only",
                                  "dueAt": "%s",
                                  "reminderAt": "%s",
                                  "status": "OPEN"
                                }
                                """.formatted(leadId, assignedToId, dueAt, reminderAt)))
                .andExpect(status().isOk());

        assertThat(outboxEventRepository.findByAggregateIdAndEventType(
                        UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED))
                .hasSize(1)
                .allMatch(e -> e.getStatus() == OutboxEventStatus.PENDING);

        Instant newReminderAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Reschedule me - title only",
                                  "dueAt": "%s",
                                  "reminderAt": "%s",
                                  "status": "OPEN"
                                }
                                """.formatted(leadId, assignedToId, dueAt, newReminderAt)))
                .andExpect(status().isOk());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(
                UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(events).hasSize(2);
        assertThat(events.stream().filter(e -> e.getStatus() == OutboxEventStatus.SUPERSEDED)).hasSize(1);
        OutboxEvent active = events.stream()
                .filter(e -> e.getStatus() == OutboxEventStatus.PENDING)
                .findFirst()
                .orElseThrow();
        assertThat(active.getAvailableAt()).isEqualTo(newReminderAt);
        FollowUpScheduledPayload latest =
                objectMapper.readValue(active.getPayload(), FollowUpScheduledPayload.class);
        assertThat(latest.reminderAt()).isEqualTo(newReminderAt);
    }

    @Test
    void removeReminderAt_supersedesPendingWithoutReplacement() throws Exception {
        String token = register("outbox.clear@example.com", "Outbox Clear");
        String leadId = createLead(token, "Clear Lead");

        Instant dueAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Clear reminder",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String taskId = created.get("id").asText();
        String assignedToId = created.get("assignedToId").asText();

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Clear reminder",
                                  "dueAt": "%s",
                                  "status": "OPEN"
                                }
                                """.formatted(leadId, assignedToId, dueAt)))
                .andExpect(status().isOk());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(
                UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(OutboxEventStatus.SUPERSEDED);
        assertThat(outboxEventRepository.countByEventTypeAndStatus(
                        OutboxEventType.FOLLOW_UP_SCHEDULED, OutboxEventStatus.PENDING))
                .isZero();
    }

    @Test
    void completeTask_supersedesPendingReminder() throws Exception {
        String token = register("outbox.complete@example.com", "Outbox Complete");
        String leadId = createLead(token, "Complete Lead");

        Instant dueAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Complete me",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();

        String taskId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(
                UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(OutboxEventStatus.SUPERSEDED);
    }

    @Test
    void cancelTask_supersedesPendingReminder() throws Exception {
        String token = register("outbox.cancel@example.com", "Outbox Cancel");
        String leadId = createLead(token, "Cancel Lead");

        Instant dueAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult createResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Cancel me",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String taskId = created.get("id").asText();
        String assignedToId = created.get("assignedToId").asText();

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Cancel me",
                                  "dueAt": "%s",
                                  "reminderAt": "%s",
                                  "status": "CANCELLED"
                                }
                                """.formatted(leadId, assignedToId, dueAt, reminderAt)))
                .andExpect(status().isOk());

        List<OutboxEvent> events = outboxEventRepository.findByAggregateIdAndEventType(
                UUID.fromString(taskId), OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(OutboxEventStatus.SUPERSEDED);
        assertThat(outboxEventRepository.countByEventTypeAndStatus(
                        OutboxEventType.FOLLOW_UP_SCHEDULED, OutboxEventStatus.PENDING))
                .isZero();
    }

    @Test
    void duePendingEvent_isReturnedByDueQuery_futureIsNot() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);

        OutboxEvent due = new OutboxEvent();
        due.setAggregateType(OutboxEventRecorder.AGGREGATE_TYPE_TASK);
        due.setAggregateId(UUID.randomUUID());
        due.setEventType(OutboxEventType.FOLLOW_UP_SCHEDULED);
        due.setPayload("{\"taskId\":\"" + due.getAggregateId() + "\"}");
        due.setStatus(OutboxEventStatus.PENDING);
        due.setAvailableAt(past);
        outboxEventRepository.save(due);

        OutboxEvent notDue = new OutboxEvent();
        notDue.setAggregateType(OutboxEventRecorder.AGGREGATE_TYPE_TASK);
        notDue.setAggregateId(UUID.randomUUID());
        notDue.setEventType(OutboxEventType.FOLLOW_UP_SCHEDULED);
        notDue.setPayload("{\"taskId\":\"" + notDue.getAggregateId() + "\"}");
        notDue.setStatus(OutboxEventStatus.PENDING);
        notDue.setAvailableAt(future);
        outboxEventRepository.save(notDue);

        List<OutboxEvent> found = outboxEventRepository.findDueByStatus(
                OutboxEventStatus.PENDING,
                Instant.now(),
                org.springframework.data.domain.PageRequest.of(0, 50));

        assertThat(found).extracting(OutboxEvent::getId).containsExactly(due.getId());
    }

    private String createLead(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s@example.com",
                                  "source": "WEB"
                                }
                                """.formatted(fullName, fullName.replace(" ", "").toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "fullName": "%s"
                                }
                                """.formatted(email, fullName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
