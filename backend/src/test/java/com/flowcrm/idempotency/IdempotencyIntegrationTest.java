package com.flowcrm.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.outbox.OutboxEvent;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.outbox.OutboxEventType;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyIntegrationTest {

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
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void cleanDatabase() {
        processedMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        contactRepository.deleteAll();
        accountRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createLead_withoutIdempotencyKey_behavesNormally() throws Exception {
        String token = register("idem.noleadkey@example.com", "No Key User");

        mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Alice NoKey", "alice.nokey@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Alice NoKey"));

        assertThat(leadRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isZero();
    }

    @Test
    void createLead_sameKeySameBody_replaysSameLeadWithoutIncreasingCount() throws Exception {
        String token = register("idem.lead.replay@example.com", "Lead Replay");
        String key = "lead-key-1";
        String body = leadBody("Replay Lead", "replay.lead@example.com");

        MvcResult first = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Replay Lead"))
                .andReturn();

        String leadId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        assertThat(leadRepository.count()).isEqualTo(1);

        MvcResult second = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(leadId))
                .andExpect(jsonPath("$.fullName").value("Replay Lead"))
                .andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(leadRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void createLead_sameKeyDifferentBody_returns409() throws Exception {
        String token = register("idem.lead.conflict@example.com", "Lead Conflict");
        String key = "lead-key-conflict";

        mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("First Body", "first.body@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Second Body", "second.body@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Idempotency key already used with a different request"));

        assertThat(leadRepository.count()).isEqualTo(1);
    }

    @Test
    void createLead_sameKey_mayBeUsedIndependentlyByDifferentUsers() throws Exception {
        String tokenA = register("idem.user.a@example.com", "User A");
        String tokenB = register("idem.user.b@example.com", "User B");
        String key = "shared-key-across-users";

        MvcResult a = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Lead A", "lead.a@example.com")))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult b = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + tokenB)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Lead B", "lead.b@example.com")))
                .andExpect(status().isCreated())
                .andReturn();

        String idA = objectMapper.readTree(a.getResponse().getContentAsString()).get("id").asText();
        String idB = objectMapper.readTree(b.getResponse().getContentAsString()).get("id").asText();
        assertThat(idA).isNotEqualTo(idB);
        assertThat(leadRepository.count()).isEqualTo(2);
    }

    @Test
    void sameKey_mayBeUsedIndependentlyForDifferentOperations() throws Exception {
        String token = register("idem.ops@example.com", "Ops User");
        String key = "cross-operation-key";
        String leadId = createLead(token, "Ops Lead");

        Instant dueAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        MvcResult leadResult = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Ops Lead Keyed", "ops.keyed@example.com")))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult taskResult = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody(leadId, "Ops Task", dueAt, null)))
                .andExpect(status().isCreated())
                .andReturn();

        String createdLeadId =
                objectMapper.readTree(leadResult.getResponse().getContentAsString()).get("id").asText();
        String createdTaskId =
                objectMapper.readTree(taskResult.getResponse().getContentAsString()).get("id").asText();
        assertThat(createdLeadId).isNotEqualTo(createdTaskId);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(2);
    }

    @Test
    void createTask_sameKeySameBody_replaysSameTaskWithoutDuplicateOutbox() throws Exception {
        String token = register("idem.task.replay@example.com", "Task Replay");
        String leadId = createLead(token, "Task Replay Lead");
        String key = "task-key-1";
        Instant dueAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        String body = taskBody(leadId, "Call back", dueAt, reminderAt);

        MvcResult first = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Call back"))
                .andReturn();

        UUID taskId = UUID.fromString(
                objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());
        assertThat(taskRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.findByAggregateIdAndEventType(taskId, OutboxEventType.FOLLOW_UP_SCHEDULED))
                .hasSize(1);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(taskId.toString()));

        assertThat(taskRepository.count()).isEqualTo(1);
        List<OutboxEvent> events =
                outboxEventRepository.findByAggregateIdAndEventType(taskId, OutboxEventType.FOLLOW_UP_SCHEDULED);
        assertThat(events).hasSize(1);
    }

    @Test
    void createLead_blankIdempotencyKey_returnsBadRequest() throws Exception {
        String token = register("idem.blank@example.com", "Blank Key");

        mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody("Blank Key Lead", "blank.key@example.com")))
                .andExpect(status().isBadRequest());

        assertThat(leadRepository.count()).isZero();
    }

    @Test
    void createLead_concurrentSameKey_createsOnlyOneLead() throws Exception {
        String token = register("idem.concurrent@example.com", "Concurrent User");
        String key = "concurrent-lead-key";
        String body = leadBody("Concurrent Lead", "concurrent.lead@example.com");

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/leads")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<String> ids = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get(10, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            ids.add(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        }
        executor.shutdownNow();

        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(leadRepository.count()).isEqualTo(1);
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

    private String createLead(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadBody(fullName, fullName.toLowerCase().replace(' ', '.') + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String leadBody(String fullName, String email) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "phone": "555-0100",
                  "company": "Acme",
                  "source": "WEB"
                }
                """.formatted(fullName, email);
    }

    private static String taskBody(String leadId, String title, Instant dueAt, Instant reminderAt) {
        if (reminderAt == null) {
            return """
                    {
                      "leadId": "%s",
                      "title": "%s",
                      "dueAt": "%s"
                    }
                    """.formatted(leadId, title, dueAt);
        }
        return """
                {
                  "leadId": "%s",
                  "title": "%s",
                  "dueAt": "%s",
                  "reminderAt": "%s"
                }
                """.formatted(leadId, title, dueAt, reminderAt);
    }
}
