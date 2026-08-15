package com.flowcrm.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class NotificationIntegrationTest {

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
    private MeetingRepository meetingRepository;

    @Autowired
    private CallRepository callRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDatabase() {
        processedMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
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
    void assignment_notifiesNewTarget_notSelf_andInboxIsPrivate() throws Exception {
        Auth admin = register("n.admin@example.com", "Admin");
        Auth rep = register("n.rep@example.com", "Rep");
        Auth other = register("n.other@example.com", "Other");

        mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "fullName": "Self Lead", "source": "WEB" }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        String leadId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/leads")
                                .header("Authorization", "Bearer " + admin.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "fullName": "Acme inquiry", "source": "WEB", "assignedToId": "%s" }
                                        """
                                                .formatted(rep.userId())))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Lead assigned to you"))
                .andExpect(jsonPath("$.content[0].message").value("Acme inquiry"))
                .andExpect(jsonPath("$.content[0].relatedEntityType").value("LEAD"))
                .andExpect(jsonPath("$.content[0].relatedEntityId").value(leadId))
                .andExpect(jsonPath("$.content[0].readAt").isEmpty());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(put("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "fullName": "Acme inquiry",
                                  "source": "WEB",
                                  "status": "NEW",
                                  "assignedToId": "%s"
                                }
                                """
                                        .formatted(rep.userId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(put("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "fullName": "Acme inquiry",
                                  "source": "WEB",
                                  "status": "NEW",
                                  "assignedToId": "%s"
                                }
                                """
                                        .formatted(other.userId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Lead assigned to you"));
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void taskMeetingCall_assignment_andReadOperations() throws Exception {
        Auth admin = register("n2.admin@example.com", "Admin");
        Auth rep = register("n2.rep@example.com", "Rep");
        Auth other = register("n2.other@example.com", "Other");
        String accountId = createAccount(admin.token(), "Notify Co");
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "accountId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Follow up with Acme",
                                  "dueAt": "%s"
                                }
                                """
                                        .formatted(accountId, rep.userId(), start)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "accountId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Discovery call",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """
                                        .formatted(accountId, rep.userId(), start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "accountId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Intro call",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND"
                                }
                                """
                                        .formatted(accountId, rep.userId(), start)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].createdAt").exists());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        JsonNode inbox = objectMapper.readTree(mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + rep.token()))
                .andReturn()
                .getResponse()
                .getContentAsString());
        String firstId = inbox.get("content").get(0).get("id").asText();
        String secondCreated = inbox.get("content").get(1).get("createdAt").asText();
        String firstCreated = inbox.get("content").get(0).get("createdAt").asText();
        org.assertj.core.api.Assertions.assertThat(firstCreated.compareTo(secondCreated)).isGreaterThanOrEqualTo(0);

        mockMvc.perform(patch("/api/v1/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mockMvc.perform(patch("/api/v1/notifications/" + firstId + "/read")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private record Auth(String token, String userId) {}

    private Auth register(String email, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "%s", "password": "password123", "fullName": "%s" }
                                """
                                        .formatted(email, fullName)))
                .andExpect(status().isCreated())
                .andReturn();
        var tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Auth(tree.get("accessToken").asText(), tree.get("user").get("id").asText());
    }

    private String createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "%s" }
                                """
                                        .formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
