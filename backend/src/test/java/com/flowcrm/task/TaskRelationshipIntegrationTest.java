package com.flowcrm.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.enums.RelatedRecordType;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.outbox.FollowUpScheduledPayload;
import com.flowcrm.outbox.OutboxEvent;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.outbox.OutboxEventType;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class TaskRelationshipIntegrationTest {

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

    @BeforeEach
    void cleanDatabase() {
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
    void create_forEachRelatedType() throws Exception {
        String token = register("rel.all@example.com", "Rel User");
        String leadId = createLead(token, "Rel Lead");
        String accountId = createAccount(token, "Rel Co");
        String contactId = createContact(token, accountId);
        String dealId = createDeal(token, accountId, "Rel Deal");
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("leadId", leadId, "Lead task", due, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("LEAD"))
                .andExpect(jsonPath("$.relatedName").value("Rel Lead"));

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("accountId", accountId, "Account task", due, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("ACCOUNT"))
                .andExpect(jsonPath("$.accountName").value("Rel Co"));

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("contactId", contactId, "Contact task", due, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("CONTACT"));

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("dealId", dealId, "Deal task", due, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("DEAL"))
                .andExpect(jsonPath("$.dealName").value("Rel Deal"));

        assertThat(taskRepository.count()).isEqualTo(4);
    }

    @Test
    void create_zeroOrMultipleRelations_rejected() throws Exception {
        String token = register("rel.bad@example.com", "Bad Rel");
        String leadId = createLead(token, "Bad Lead");
        String accountId = createAccount(token, "Bad Co");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "None", "dueAt": "%s" }
                                """.formatted(due)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "accountId": "%s",
                                  "title": "Both",
                                  "dueAt": "%s"
                                }
                                """.formatted(leadId, accountId, due)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_canRetargetRelation() throws Exception {
        String token = register("rel.upd@example.com", "Upd Rel");
        String leadId = createLead(token, "From Lead");
        String accountId = createAccount(token, "To Co");
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);
        String taskId = createTask(token, "leadId", leadId, "Move me", due);
        String assignee = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/tasks/" + taskId).header("Authorization", "Bearer " + token))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("assignedToId")
                .asText();

        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Moved",
                                  "dueAt": "%s",
                                  "status": "OPEN"
                                }
                                """.formatted(accountId, assignee, due)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedType").value("ACCOUNT"))
                .andExpect(jsonPath("$.leadId").isEmpty())
                .andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    void filters_byRelatedIdsAndType() throws Exception {
        String token = register("rel.filt@example.com", "Filt");
        String leadId = createLead(token, "Filt Lead");
        String accountId = createAccount(token, "Filt Co");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);
        String leadTask = createTask(token, "leadId", leadId, "L", due);
        createTask(token, "accountId", accountId, "A", due);

        mockMvc.perform(get("/api/v1/tasks").param("leadId", leadId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(leadTask));

        mockMvc.perform(get("/api/v1/tasks").param("accountId", accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/tasks").param("relatedType", "DEAL").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/tasks").param("relatedType", "LEAD").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void salesRep_cannotLinkInaccessibleAccount() throws Exception {
        String adminToken = register("rel.admin@example.com", "Admin");
        String repToken = register("rel.rep@example.com", "Rep");
        String hidden = createAccount(adminToken, "Hidden Co");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("accountId", hidden, "Steal", due, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesRep_canCreateOnOwnLeadAccountContactDeal() throws Exception {
        register("rel.own.admin@example.com", "Admin");
        String repToken = register("rel.own.rep@example.com", "Rep");
        String leadId = createLead(repToken, "Own Lead");
        String accountId = createAccount(repToken, "Own Co");
        String contactId = createContact(repToken, accountId);
        String dealId = createDeal(repToken, accountId, "Own Deal");
        Instant due = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("leadId", leadId, "L", due, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("accountId", accountId, "A", due, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("contactId", contactId, "C", due, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody("dealId", dealId, "D", due, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void reminders_forNonLeadTasks_writeRelatedMetadata() throws Exception {
        String token = register("rel.rem@example.com", "Rem");
        String accountId = createAccount(token, "Rem Co");
        String contactId = createContact(token, accountId);
        String dealId = createDeal(token, accountId, "Rem Deal");
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant reminder = Instant.now().plus(1, ChronoUnit.DAYS);

        createTask(token, "accountId", accountId, "Acct follow", due, reminder);
        createTask(token, "contactId", contactId, "Contact follow", due, reminder);
        createTask(token, "dealId", dealId, "Deal follow", due, reminder);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(3);
        List<RelatedRecordType> types = events.stream()
                .map(event -> {
                    try {
                        return objectMapper.readValue(event.getPayload(), FollowUpScheduledPayload.class).relatedType();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .toList();
        assertThat(types)
                .containsExactlyInAnyOrder(RelatedRecordType.ACCOUNT, RelatedRecordType.CONTACT, RelatedRecordType.DEAL);
    }

    @Test
    void delete_blockedWhileTasksExist() throws Exception {
        String token = register("rel.del@example.com", "Del");
        String accountId = createAccount(token, "Keep Co");
        createTask(token, "accountId", accountId, "Block delete", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    private String createTask(String token, String field, String relatedId, String title, Instant dueAt)
            throws Exception {
        return createTask(token, field, relatedId, title, dueAt, null);
    }

    private String createTask(
            String token, String field, String relatedId, String title, Instant dueAt, Instant reminderAt)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(relatedBody(field, relatedId, title, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String relatedBody(String field, String relatedId, String title, Instant dueAt, Instant reminderAt) {
        String reminder = reminderAt == null ? "" : ", \"reminderAt\": \"%s\"".formatted(reminderAt);
        return """
                {
                  "%s": "%s",
                  "title": "%s",
                  "dueAt": "%s"
                  %s
                }
                """.formatted(field, relatedId, title, dueAt, reminder);
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

    private String createContact(String token, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Pat", "lastName": "Lee", "accountId": "%s" }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createDeal(String token, String accountId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "accountId": "%s" }
                                """.formatted(name, accountId)))
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
