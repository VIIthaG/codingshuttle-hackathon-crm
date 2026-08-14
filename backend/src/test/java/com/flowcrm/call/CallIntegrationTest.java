package com.flowcrm.call;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
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
class CallIntegrationTest {

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
        String token = register("call.all@example.com", "Call User");
        String leadId = createLead(token, "Call Lead");
        String accountId = createAccount(token, "Call Co");
        String contactId = createContact(token, accountId);
        String dealId = createDeal(token, accountId, "Call Deal");
        Instant when = Instant.now().plus(3, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", leadId, "Lead call", when, "OUTBOUND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("LEAD"))
                .andExpect(jsonPath("$.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("accountId", accountId, "Account call", when, "INBOUND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("ACCOUNT"));

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("contactId", contactId, "Contact call", when, "OUTBOUND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("CONTACT"));

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dealId", dealId, "Deal call", when, "OUTBOUND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.relatedType").value("DEAL"));

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "title": "None", "scheduledAt": "%s", "direction": "OUTBOUND" }
                                """.formatted(when)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeWithOutcome_cancel_filters_idempotency_security() throws Exception {
        String token = register("call.life@example.com", "Life");
        String dealAccount = createAccount(token, "Deal Co");
        String dealId = createDeal(token, dealAccount, "Expansion");
        Instant when = Instant.now().plus(4, ChronoUnit.HOURS);

        String id = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/calls")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", "call-replay-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("dealId", dealId, "Follow up", when, "OUTBOUND")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "call-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dealId", dealId, "Follow up", when, "OUTBOUND")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        String leadId = createLead(token, "Other Lead");
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "call-replay-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("leadId", leadId, "Follow up", when, "OUTBOUND")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/calls")
                        .param("dealId", dealId)
                        .param("direction", "OUTBOUND")
                        .param("relatedType", "DEAL")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id));

        String assignee = objectMapper
                .readTree(mockMvc.perform(get("/api/v1/calls/" + id).header("Authorization", "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("assignedToId")
                .asText();

        mockMvc.perform(put("/api/v1/calls/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dealId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Follow up updated",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND",
                                  "durationMinutes": 15,
                                  "phoneNumber": "+15551212"
                                }
                                """.formatted(dealId, assignee, when)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMinutes").value(15));

        mockMvc.perform(patch("/api/v1/calls/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED", "outcome": "Reached decision maker" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("Reached decision maker"));

        mockMvc.perform(patch("/api/v1/calls/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CANCELLED" }
                                """))
                .andExpect(status().isBadRequest());

        String cancelId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/calls")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("dealId", dealId, "To cancel", when, "INBOUND")))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(patch("/api/v1/calls/" + cancelId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CANCELLED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/calls/" + cancelId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        String repToken = register("call.rep@example.com", "Rep");
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dealId", dealId, "Steal", when, "OUTBOUND")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/calls/" + id).header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
    }

    private static String body(String field, String relatedId, String title, Instant when, String direction) {
        return """
                {
                  "%s": "%s",
                  "title": "%s",
                  "scheduledAt": "%s",
                  "direction": "%s"
                }
                """.formatted(field, relatedId, title, when, direction);
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
