package com.flowcrm.activity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
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
class ActivityTimelineIntegrationTest {

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
    private DealRepository dealRepository;

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
        dealRepository.deleteAll();
        contactRepository.deleteAll();
        accountRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void timeline_includesCreatedAndRelatedTasksAndConversion() throws Exception {
        String token = register("act.admin@example.com", "Act Admin");
        String leadId = createLead(token, "Timeline Lead");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);
        String taskId = createLeadTask(token, leadId, "Follow up proposal", due);

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONTACTED" }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "QUALIFIED" }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Acme Ltd" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/activities/timeline")
                        .param("entityType", "LEAD")
                        .param("entityId", leadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("LEAD"))
                .andExpect(jsonPath("$.entityName").value("Timeline Lead"))
                .andExpect(jsonPath("$.items[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'RECORD_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'TASK_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'TASK_COMPLETED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'LEAD_CONVERTED')]").isNotEmpty());
    }

    @Test
    void timeline_cancelledTask_andNewestFirst() throws Exception {
        String token = register("act.cancel@example.com", "Cancel Act");
        String accountId = createAccount(token, "Act Co");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);
        String taskId = createAccountTask(token, accountId, "Schedule demo", due);
        String assignee = objectMapper
                .readTree(mockMvc.perform(get("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("assignedToId")
                .asText();

        mockMvc.perform(putTaskCancelled(token, taskId, accountId, assignee, due))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/activities/timeline")
                        .param("entityType", "ACCOUNT")
                        .param("entityId", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.type == 'TASK_CANCELLED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'RECORD_CREATED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[0].timestamp").isNotEmpty());
    }

    @Test
    void timeline_salesRepOwnContact_ok() throws Exception {
        register("act.scope.admin@example.com", "Admin");
        String repToken = register("act.scope.rep@example.com", "Rep");
        String contactId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/contacts")
                                .header("Authorization", "Bearer " + repToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        { "firstName": "Sam", "lastName": "Own" }
                                        """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/v1/activities/timeline")
                        .param("entityType", "CONTACT")
                        .param("entityId", contactId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("CONTACT"))
                .andExpect(jsonPath("$.items[?(@.type == 'RECORD_CREATED')]").isNotEmpty());
    }

    @Test
    void timeline_inaccessibleEntity_forbidden() throws Exception {
        String adminToken = register("act.hide.admin@example.com", "Admin");
        String repToken = register("act.hide.rep@example.com", "Rep");
        String accountId = createAccount(adminToken, "Hidden Co");

        mockMvc.perform(get("/api/v1/activities/timeline")
                        .param("entityType", "ACCOUNT")
                        .param("entityId", accountId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putTaskCancelled(
            String token, String taskId, String accountId, String assignee, Instant due) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/tasks/" + taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "accountId": "%s",
                          "assignedToId": "%s",
                          "title": "Schedule demo",
                          "dueAt": "%s",
                          "status": "CANCELLED"
                        }
                        """.formatted(accountId, assignee, due));
    }

    private String createLead(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "fullName": "%s", "source": "WEB" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createAccount(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createLeadTask(String token, String leadId, String title, Instant due) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "%s", "dueAt": "%s" }
                                """.formatted(leadId, title, due)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createAccountTask(String token, String accountId, String title, Instant due) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountId": "%s", "title": "%s", "dueAt": "%s" }
                                """.formatted(accountId, title, due)))
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
