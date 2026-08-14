package com.flowcrm.lead;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
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
class LeadConversionIntegrationTest {

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
    void convert_createsAccountAndContact_withoutDeal() throws Exception {
        String token = register("cv.basic@example.com", "Converter");
        String leadId = qualifyLead(token, "Alice Johnson", "Acme Ltd", "alice@acme.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Acme Ltd" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.convertedAt").isNotEmpty())
                .andExpect(jsonPath("$.convertedAccountName").value("Acme Ltd"))
                .andExpect(jsonPath("$.convertedContactName").value("Alice Johnson"))
                .andExpect(jsonPath("$.convertedDealId").isEmpty());

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(contactRepository.count()).isEqualTo(1);
        assertThat(dealRepository.count()).isZero();
        assertThat(leadRepository.findById(java.util.UUID.fromString(leadId)).orElseThrow().getStatus().name())
                .isEqualTo("CONVERTED");
    }

    @Test
    void convert_withDeal_setsDealReferenceAndDashboard() throws Exception {
        String token = register("cv.deal@example.com", "Deal Conv");
        String leadId = qualifyLead(token, "Bob Smith", "Globex", "bob@globex.example");

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadsByStatus.QUALIFIED").value(1))
                .andExpect(jsonPath("$.openDeals").value(0));

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Globex",
                                  "createDeal": true,
                                  "dealName": "Globex Opportunity",
                                  "amount": 5000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedDealName").value("Globex Opportunity"));

        assertThat(dealRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leadsByStatus.CONVERTED").value(1))
                .andExpect(jsonPath("$.openDeals").value(1))
                .andExpect(jsonPath("$.openPipelineValue").value(5000.0));
    }

    @Test
    void convert_usesExistingAccountAndContact() throws Exception {
        String token = register("cv.exist@example.com", "Exist");
        String accountId = createAccount(token, "Existing Co");
        String contactId = createContact(token, "Pat", "Lee", accountId);
        String leadId = qualifyLead(token, "Pat Lee", "Existing Co", "pat@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "useExistingAccountId": "%s",
                                  "useExistingContactId": "%s"
                                }
                                """.formatted(accountId, contactId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAccountId").value(accountId))
                .andExpect(jsonPath("$.convertedContactId").value(contactId));

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(contactRepository.count()).isEqualTo(1);
    }

    @Test
    void convert_inaccessibleAccount_forbidden() throws Exception {
        String adminToken = register("cv.hide.admin@example.com", "Admin");
        String repToken = register("cv.hide.rep@example.com", "Rep");
        String hidden = createAccount(adminToken, "Hidden Co");
        String leadId = qualifyLead(repToken, "Rep Lead", "Rep Co", "rep@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "useExistingAccountId": "%s" }
                                """.formatted(hidden)))
                .andExpect(status().isForbidden());
    }

    @Test
    void convert_inaccessibleContact_forbidden() throws Exception {
        String adminToken = register("cv.ct.admin@example.com", "Admin");
        String repToken = register("cv.ct.rep@example.com", "Rep");
        String adminAccount = createAccount(adminToken, "Admin Co");
        String hiddenContact = createContact(adminToken, "Hid", "Den", adminAccount);
        String leadId = qualifyLead(repToken, "Rep Lead", "Rep Co", "r2@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Rep Co",
                                  "useExistingContactId": "%s"
                                }
                                """.formatted(hiddenContact)))
                .andExpect(status().isForbidden());
    }

    @Test
    void convert_contactAccountMismatch_badRequest() throws Exception {
        String token = register("cv.mismatch@example.com", "Mismatch");
        String accountA = createAccount(token, "A Co");
        String accountB = createAccount(token, "B Co");
        String contactB = createContact(token, "Bee", "Contact", accountB);
        String leadId = qualifyLead(token, "Mismatch Lead", "A Co", "mm@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "useExistingAccountId": "%s",
                                  "useExistingContactId": "%s"
                                }
                                """.formatted(accountA, contactB)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void convert_rejectsNonQualifiedStatuses() throws Exception {
        String token = register("cv.elig@example.com", "Elig");
        String neu = createLead(token, "New Person", "NEW");
        mockMvc.perform(post("/api/v1/leads/" + neu + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "X" }
                                """))
                .andExpect(status().isConflict());

        String contacted = createLead(token, "Contacted Person", "NEW");
        patchStatus(token, contacted, "CONTACTED");
        mockMvc.perform(post("/api/v1/leads/" + contacted + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "X" }
                                """))
                .andExpect(status().isConflict());

        String lost = createLead(token, "Lost Person", "NEW");
        patchStatus(token, lost, "LOST");
        mockMvc.perform(post("/api/v1/leads/" + lost + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "X" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void convert_cannotRunTwice() throws Exception {
        String token = register("cv.twice@example.com", "Twice");
        String leadId = qualifyLead(token, "Once Only", "Once Co", "once@ex.example");
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Once Co" }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Once Co" }
                                """))
                .andExpect(status().isConflict());
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void salesRep_convertsOwnLead() throws Exception {
        register("cv.rep.admin@example.com", "Admin");
        String repToken = register("cv.rep.own@example.com", "Rep");
        String leadId = qualifyLead(repToken, "Rep Own", "Rep Co", "own@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Rep Co" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"));

        String accountId = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/leads/" + leadId).header("Authorization", "Bearer " + repToken))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .get("convertedAccountId")
                .asText();
        mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rep Co"));
    }

    @Test
    void salesRep_cannotConvertAnotherRepsLead() throws Exception {
        String adminToken = register("cv.own.admin@example.com", "Admin");
        String repToken = register("cv.own.rep@example.com", "Rep");
        String leadId = qualifyLead(adminToken, "Admin Lead", "Admin Co", "al@ex.example");

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Stolen" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void convert_missingDealName_rollsBackAccountAndContact() throws Exception {
        String token = register("cv.rollback@example.com", "Rollback");
        String leadId = qualifyLead(token, "Roll Back", "Roll Co", "rb@ex.example");
        long accounts = accountRepository.count();
        long contacts = contactRepository.count();

        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Roll Co",
                                  "createDeal": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(accountRepository.count()).isEqualTo(accounts);
        assertThat(contactRepository.count()).isEqualTo(contacts);
        assertThat(dealRepository.count()).isZero();
        mockMvc.perform(get("/api/v1/leads/" + leadId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"))
                .andExpect(jsonPath("$.convertedAt").isEmpty());
    }

    @Test
    void convert_idempotentReplay_andMismatch409_andDifferentLead() throws Exception {
        String token = register("cv.idem@example.com", "Idem");
        String leadA = qualifyLead(token, "Idem A", "Idem A Co", "a@idem.example");
        String leadB = qualifyLead(token, "Idem B", "Idem B Co", "b@idem.example");
        String key = "convert-key-1";
        String body = """
                { "accountName": "Idem A Co" }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/leads/" + leadA + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String accountId = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("convertedAccountId")
                .asText();

        mockMvc.perform(post("/api/v1/leads/" + leadA + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAccountId").value(accountId));

        mockMvc.perform(post("/api/v1/leads/" + leadA + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Different Co" }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/leads/" + leadB + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Idem B Co" }
                                """))
                .andExpect(status().isConflict());

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    void convert_concurrentRequests_doNotDuplicate() throws Exception {
        String token = register("cv.race@example.com", "Race");
        String leadId = qualifyLead(token, "Race Lead", "Race Co", "race@ex.example");
        String body = """
                { "accountName": "Race Co", "createDeal": true, "dealName": "Race Opportunity" }
                """;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            statuses.add(pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> future : statuses) {
            codes.add(future.get());
        }
        assertThat(codes).contains(200);
        assertThat(codes.stream().filter(code -> code == 200).count()).isEqualTo(1);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(contactRepository.count()).isEqualTo(1);
        assertThat(dealRepository.count()).isEqualTo(1);
    }

    @Test
    void convert_singleWordLeadName_doesNotFail() throws Exception {
        String token = register("cv.oneword@example.com", "One Word");
        String leadId = qualifyLead(token, "Madonna", null, "madonna@ex.example");
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Madonna LLC" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedContactName").value("Madonna Madonna"));
    }

    private String qualifyLead(String token, String fullName, String company, String email) throws Exception {
        String leadId = createLead(token, fullName, "NEW", company, email);
        patchStatus(token, leadId, "CONTACTED");
        patchStatus(token, leadId, "QUALIFIED");
        return leadId;
    }

    private void patchStatus(String token, String leadId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "%s" }
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private String createLead(String token, String fullName, String status) throws Exception {
        return createLead(token, fullName, status, "Co", fullName.replace(" ", "").toLowerCase() + "@ex.example");
    }

    private String createLead(String token, String fullName, String status, String company, String email)
            throws Exception {
        String companyJson = company == null ? "" : ", \"company\": \"%s\"".formatted(company);
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s",
                                  "source": "WEB",
                                  "status": "%s"
                                  %s
                                }
                                """.formatted(fullName, email, status, companyJson)))
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
                                {
                                  "firstName": "%s",
                                  "lastName": "%s",
                                  "accountId": "%s"
                                }
                                """.formatted(first, last, accountId)))
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
