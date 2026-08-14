package com.flowcrm.meeting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.call.CallRepository;
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
class MeetingIntegrationTest {

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
    void create_forEachRelatedType_andRejectInvalid() throws Exception {
        String token = register("mtg.all@example.com", "Mtg User");
        String leadId = createLead(token, "Mtg Lead");
        String accountId = createAccount(token, "Mtg Co");
        String contactId = createContact(token, accountId);
        String dealId = createDeal(token, accountId, "Mtg Deal");
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", leadId, "Lead meeting", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("LEAD"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("accountId", accountId, "Account meeting", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("ACCOUNT"));

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("contactId", contactId, "Contact meeting", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("CONTACT"));

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dealId", dealId, "Deal meeting", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("DEAL"));

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "None", "startAt": "%s", "endAt": "%s" }
                                """.formatted(start, end)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "accountId": "%s",
                                  "title": "Both",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(leadId, accountId, start, end)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", leadId, "Bad times", end, start)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lifecycle_filters_update_delete_andIdempotency() throws Exception {
        String token = register("mtg.life@example.com", "Life");
        String leadId = createLead(token, "Life Lead");
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        String id = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/meetings")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", "mtg-replay-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("leadId", leadId, "Discovery", start, end)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "mtg-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", leadId, "Discovery", start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        String accountId = createAccount(token, "Other Co");
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "mtg-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("accountId", accountId, "Discovery", start, end)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/meetings")
                        .param("leadId", leadId)
                        .param("status", "SCHEDULED")
                        .param("relatedType", "LEAD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));

        String assignee = objectMapper
                .readTree(mockMvc.perform(get("/api/v1/meetings/" + id).header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("assignedToId")
                .asText();

        Instant newStart = start.plus(1, ChronoUnit.HOURS);
        Instant newEnd = newStart.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(put("/api/v1/meetings/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Discovery updated",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(leadId, assignee, newStart, newEnd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Discovery updated"));

        mockMvc.perform(patch("/api/v1/meetings/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(patch("/api/v1/meetings/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CANCELLED" }
                                """))
                .andExpect(status().isBadRequest());

        String cancelId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/meetings")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("leadId", leadId, "To cancel", start, end)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(patch("/api/v1/meetings/" + cancelId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CANCELLED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/api/v1/meetings/" + cancelId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void salesRep_cannotUseInaccessibleRelation_andParentDeleteConflicts() throws Exception {
        String adminToken = register("mtg.admin@example.com", "Admin");
        String repToken = register("mtg.rep@example.com", "Rep");
        String hiddenLead = createLead(adminToken, "Hidden Lead");
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", hiddenLead, "Steal", start, end)))
                .andExpect(status().isForbidden());

        String ownLead = createLead(repToken, "Own Lead");
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", ownLead, "Own meeting", start, end)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/leads/" + ownLead).header("Authorization", "Bearer " + repToken))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/meetings").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].relatedName").value("Own Lead"));
    }

    private static String body(String field, String relatedId, String title, Instant start, Instant end) {
        return """
                {
                  "%s": "%s",
                  "title": "%s",
                  "startAt": "%s",
                  "endAt": "%s"
                }
                """.formatted(field, relatedId, title, start, end);
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
