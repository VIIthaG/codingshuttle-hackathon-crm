package com.flowcrm.lead;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
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
class LeadIntegrationTest {

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
    private com.flowcrm.reminder.ProcessedMessageRepository processedMessageRepository;

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
    void createLead_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Jane Doe",
                                  "email": "jane@acme.com",
                                  "phone": "555-0100",
                                  "company": "Acme",
                                  "source": "WEB"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createAndGetLead_succeeds() throws Exception {
        String token = registerAndGetToken("lead.owner@example.com", "Lead Owner");

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Jane Doe",
                                  "email": "Jane@Acme.com",
                                  "phone": "555-0100",
                                  "company": "Acme",
                                  "source": "WEB"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.email").value("jane@acme.com"))
                .andExpect(jsonPath("$.company").value("Acme"))
                .andExpect(jsonPath("$.source").value("WEB"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.assignedToId").isNotEmpty())
                .andReturn();

        String leadId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leadId))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"));
    }

    @Test
    void listUpdateDeleteLead_succeeds() throws Exception {
        String token = registerAndGetToken("lead.crud@example.com", "CRUD User");

        MvcResult createResult = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "John Smith",
                                  "email": "john@example.com",
                                  "source": "REFERRAL",
                                  "status": "NEW"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String leadId = created.get("id").asText();
        String assignedToId = created.get("assignedToId").asText();

        mockMvc.perform(get("/api/v1/leads")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(leadId));

        mockMvc.perform(put("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "John Smith Updated",
                                  "email": "john.updated@example.com",
                                  "phone": "555-9999",
                                  "company": "Example Co",
                                  "source": "EVENT",
                                  "status": "CONTACTED",
                                  "assignedToId": "%s"
                                }
                                """.formatted(assignedToId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Smith Updated"))
                .andExpect(jsonPath("$.status").value("CONTACTED"))
                .andExpect(jsonPath("$.source").value("EVENT"));

        mockMvc.perform(delete("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
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
