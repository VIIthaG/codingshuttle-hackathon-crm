package com.flowcrm.deal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
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
class DealPipelineIntegrationTest {

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
    private DealRepository dealRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

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
    void happyPath_toClosedWon_setsProbability100() throws Exception {
        String token = register("pipe.won@example.com", "Won");
        String dealId = createDeal(token);

        patchStage(token, dealId, "QUALIFICATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("QUALIFICATION"))
                .andExpect(jsonPath("$.probability").value(25));
        patchStage(token, dealId, "PROPOSAL").andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(50));
        patchStage(token, dealId, "NEGOTIATION").andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(75));
        patchStage(token, dealId, "CLOSED_WON").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CLOSED_WON"))
                .andExpect(jsonPath("$.probability").value(100));

        patchStage(token, dealId, "CLOSED_LOST").andExpect(status().isBadRequest());
        patchStage(token, dealId, "NEGOTIATION").andExpect(status().isBadRequest());
    }

    @Test
    void canLoseFromEachOpenStage_andIsTerminal() throws Exception {
        String token = register("pipe.lost@example.com", "Lost");

        String fromProspecting = createDeal(token, "Lose P");
        mockMvc.perform(patch("/api/v1/deals/" + fromProspecting + "/stage")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stage": "CLOSED_LOST", "lostReason": "No budget" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CLOSED_LOST"))
                .andExpect(jsonPath("$.probability").value(0))
                .andExpect(jsonPath("$.lostReason").value("No budget"));
        patchStage(token, fromProspecting, "PROSPECTING").andExpect(status().isBadRequest());

        String fromQual = createDeal(token, "Lose Q");
        patchStage(token, fromQual, "QUALIFICATION").andExpect(status().isOk());
        patchStage(token, fromQual, "CLOSED_LOST").andExpect(status().isOk())
                .andExpect(jsonPath("$.probability").value(0));

        String fromProposal = createDeal(token, "Lose PR");
        patchStage(token, fromProposal, "QUALIFICATION").andExpect(status().isOk());
        patchStage(token, fromProposal, "PROPOSAL").andExpect(status().isOk());
        patchStage(token, fromProposal, "CLOSED_LOST").andExpect(status().isOk());

        String fromNegotiation = createDeal(token, "Lose N");
        patchStage(token, fromNegotiation, "QUALIFICATION").andExpect(status().isOk());
        patchStage(token, fromNegotiation, "PROPOSAL").andExpect(status().isOk());
        patchStage(token, fromNegotiation, "NEGOTIATION").andExpect(status().isOk());
        patchStage(token, fromNegotiation, "CLOSED_LOST").andExpect(status().isOk());
    }

    @Test
    void invalidStageJump_isRejected() throws Exception {
        String token = register("pipe.jump@example.com", "Jump");
        String dealId = createDeal(token);

        patchStage(token, dealId, "PROPOSAL").andExpect(status().isBadRequest());
        patchStage(token, dealId, "NEGOTIATION").andExpect(status().isBadRequest());
        patchStage(token, dealId, "CLOSED_WON").andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions patchStage(String token, String dealId, String stage)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/deals/" + dealId + "/stage")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "stage": "%s" }
                        """.formatted(stage)));
    }

    private String createDeal(String token) throws Exception {
        return createDeal(token, "Pipeline Deal");
    }

    private String createDeal(String token, String name) throws Exception {
        String accountId = createAccount(token, name + " Co");
        MvcResult result = mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "accountId": "%s", "amount": 1000 }
                                """.formatted(name, accountId)))
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
