package com.flowcrm.deal;

import static org.assertj.core.api.Assertions.assertThat;
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
class DealIntegrationTest {

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
    void createGetListUpdateDeleteDeal_succeeds() throws Exception {
        String token = register("deal.crud@example.com", "Deal Owner");
        String accountId = createAccount(token, "Acme Corp");
        String contactId = createContact(token, "Jane", "Doe", accountId);

        MvcResult created = mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme expansion",
                                  "accountId": "%s",
                                  "primaryContactId": "%s",
                                  "amount": 10000,
                                  "expectedCloseDate": "2026-12-01",
                                  "description": "Upsell"
                                }
                                """.formatted(accountId, contactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme expansion"))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.accountName").value("Acme Corp"))
                .andExpect(jsonPath("$.primaryContactId").value(contactId))
                .andExpect(jsonPath("$.primaryContactName").value("Jane Doe"))
                .andExpect(jsonPath("$.stage").value("PROSPECTING"))
                .andExpect(jsonPath("$.probability").value(10))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();

        String dealId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dealId));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "expansion")
                        .param("accountId", accountId)
                        .param("stage", "PROSPECTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(put("/api/v1/deals/" + dealId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme expansion v2",
                                  "accountId": "%s",
                                  "primaryContactId": "%s",
                                  "ownerId": "%s",
                                  "stage": "PROSPECTING",
                                  "amount": 12000,
                                  "currency": "USD",
                                  "probability": 15,
                                  "expectedCloseDate": "2026-12-15",
                                  "description": "Updated"
                                }
                                """.formatted(accountId, contactId, meId(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme expansion v2"))
                .andExpect(jsonPath("$.probability").value(15));

        mockMvc.perform(delete("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDeal_withoutAccount_returnsBadRequest() throws Exception {
        String token = register("deal.noacct@example.com", "No Acct");
        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Orphan deal" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDeal_withoutContact_succeeds() throws Exception {
        String token = register("deal.nocontact@example.com", "No Contact");
        String accountId = createAccount(token, "Solo Co");
        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "No contact deal", "accountId": "%s" }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryContactId").isEmpty());
    }

    @Test
    void createDeal_contactAccountMismatch_returnsBadRequest() throws Exception {
        String token = register("deal.mismatch@example.com", "Mismatch");
        String accountA = createAccount(token, "A Co");
        String accountB = createAccount(token, "B Co");
        String contactB = createContact(token, "Bob", "B", accountB);

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wrong contact",
                                  "accountId": "%s",
                                  "primaryContactId": "%s"
                                }
                                """.formatted(accountA, contactB)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesRep_cannotLinkInaccessibleAccount() throws Exception {
        String adminToken = register("deal.hidden.admin@example.com", "Admin");
        String repToken = register("deal.hidden.rep@example.com", "Rep");
        String hiddenAccount = createAccount(adminToken, "Hidden Co");

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Steal", "accountId": "%s" }
                                """.formatted(hiddenAccount)))
                .andExpect(status().isForbidden());
    }

    @Test
    void salesRep_cannotSeeOrMutateAnotherUsersDeal() throws Exception {
        String adminToken = register("deal.iso.admin@example.com", "Admin");
        String repToken = register("deal.iso.rep@example.com", "Rep");
        String accountId = createAccount(adminToken, "Admin Co");
        String dealId = createDeal(adminToken, "Admin Deal", accountId);

        mockMvc.perform(get("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/deals").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(delete("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canListAllAndAssignOwner_andFilterByOwnerAccountSearch() throws Exception {
        String adminToken = register("deal.assign.admin@example.com", "Assign Admin");
        String repToken = register("deal.assign.rep@example.com", "Assign Rep");
        String repId = meId(repToken);
        String accountId = createAccount(adminToken, "Pipeline Co");
        String contactId = createContact(adminToken, "Pat", "Contact", accountId);

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Assigned Deal",
                                  "accountId": "%s",
                                  "primaryContactId": "%s",
                                  "ownerId": "%s"
                                }
                                """.formatted(accountId, contactId, repId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(repId));

        createDeal(adminToken, "Admin Only Deal", accountId);

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("ownerId", repId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Pipeline Co"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/deals")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("search", "Pat Contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/deals").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void salesRep_cannotAssignDealToAnotherUser() throws Exception {
        String adminToken = register("deal.noassign.admin@example.com", "Admin");
        String repToken = register("deal.noassign.rep@example.com", "Rep");
        String adminId = meId(adminToken);
        String accountId = createAccount(repToken, "Rep Co");

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mine",
                                  "accountId": "%s",
                                  "ownerId": "%s"
                                }
                                """.formatted(accountId, adminId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeleteAccount_whileDealsExist() throws Exception {
        String token = register("deal.acctlock@example.com", "Lock");
        String accountId = createAccount(token, "Locked Co");
        createDeal(token, "Blocking deal", accountId);

        mockMvc.perform(delete("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingContact_unlinksPrimaryContact() throws Exception {
        String token = register("deal.unlink@example.com", "Unlink");
        String accountId = createAccount(token, "Keep Co");
        String contactId = createContact(token, "Temp", "Person", accountId);
        String dealId = createDeal(token, "Linked", accountId, contactId);

        mockMvc.perform(delete("/api/v1/contacts/" + contactId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/deals/" + dealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryContactId").isEmpty());
    }

    @Test
    void createDeal_sameKeySameBody_replays() throws Exception {
        String token = register("deal.idem.replay@example.com", "Replay");
        String accountId = createAccount(token, "Idem Co");
        String key = "deal-key-1";
        String body = """
                { "name": "Replay Deal", "accountId": "%s" }
                """.formatted(accountId);

        MvcResult first = mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(dealRepository.count()).isEqualTo(1);
    }

    @Test
    void createDeal_sameKeyDifferentBody_returns409() throws Exception {
        String token = register("deal.idem.conflict@example.com", "Conflict");
        String accountId = createAccount(token, "Conflict Co");
        String key = "deal-key-conflict";

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "First Deal", "accountId": "%s" }
                                """.formatted(accountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Second Deal", "accountId": "%s" }
                                """.formatted(accountId)))
                .andExpect(status().isConflict());

        assertThat(dealRepository.count()).isEqualTo(1);
    }

    @Test
    void createDeal_negativeAmount_returnsBadRequest() throws Exception {
        String token = register("deal.neg@example.com", "Neg");
        String accountId = createAccount(token, "Neg Co");
        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Bad", "accountId": "%s", "amount": -1 }
                                """.formatted(accountId)))
                .andExpect(status().isBadRequest());
    }

    private String createDeal(String token, String name, String accountId) throws Exception {
        return createDeal(token, name, accountId, null);
    }

    private String createDeal(String token, String name, String accountId, String contactId) throws Exception {
        String body = contactId == null
                ? """
                        { "name": "%s", "accountId": "%s" }
                        """.formatted(name, accountId)
                : """
                        { "name": "%s", "accountId": "%s", "primaryContactId": "%s" }
                        """.formatted(name, accountId, contactId);
        MvcResult result = mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    private String createContact(String token, String first, String last, String accountId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "%s", "lastName": "%s", "accountId": "%s" }
                                """.formatted(first, last, accountId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String meId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
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
