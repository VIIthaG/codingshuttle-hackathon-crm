package com.flowcrm.search;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
import com.flowcrm.call.CallRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.notification.NotificationRepository;
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
class SearchIntegrationTest {

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
    void search_matchesAllEntityTypes_caseInsensitive_andBounds() throws Exception {
        Auth admin = register("search.admin@example.com", "Search Admin");
        String accountId = createAccount(admin.token(), "K6Acme Ltd", "Technology");
        createLead(admin.token(), "K6Jane Prospect", "K6Acme Ltd");
        createContact(admin.token(), accountId, "K6Pat", "Lee");
        createDeal(admin.token(), accountId, "K6Expansion Opportunity");
        Instant when = Instant.now().plus(2, ChronoUnit.DAYS);
        createTask(admin.token(), accountId, "K6Send proposal");
        createMeeting(admin.token(), accountId, "K6Discovery call", when);
        createCall(admin.token(), accountId, "K6Follow up call", when);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6acme")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("k6acme"))
                .andExpect(jsonPath("$.results[?(@.type == 'ACCOUNT')].title").value(hasItem("K6Acme Ltd")))
                .andExpect(jsonPath("$.results[?(@.type == 'LEAD')].title").value(hasItem("K6Jane Prospect")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6expansion")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'DEAL')].title").value(hasItem("K6Expansion Opportunity")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6pat")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'CONTACT')].title").value(hasItem("K6Pat Lee")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6send")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'TASK')].title").value(hasItem("K6Send proposal")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6discovery")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'MEETING')].title").value(hasItem("K6Discovery call")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6follow")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'CALL')].title").value(hasItem("K6Follow up call")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "a")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "   ")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "k6send")
                        .param("types", "TASK")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'TASK')]").isNotEmpty())
                .andExpect(jsonPath("$.results[?(@.type == 'ACCOUNT')]").isEmpty());

        for (int i = 0; i < 15; i++) {
            createAccount(admin.token(), "BoundSearch Co " + i, "Software");
        }
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "boundsearch")
                        .param("types", "ACCOUNT")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()", lessThanOrEqualTo(10)));
    }

    @Test
    void search_salesRep_doesNotLeakInaccessibleRecords() throws Exception {
        Auth admin = register("search.scope.admin@example.com", "Admin");
        Auth rep = register("search.scope.rep@example.com", "Rep");
        createAccount(admin.token(), "HiddenOmega Corp", "Stealth");
        createLead(admin.token(), "HiddenOmega Lead", "HiddenOmega Corp");
        String visibleAccount = createAccount(rep.token(), "VisibleOmega LLC", "Retail");
        createLead(rep.token(), "VisibleOmega Lead", "VisibleOmega LLC");
        createDeal(rep.token(), visibleAccount, "VisibleOmega Deal");

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "hiddenomega")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(0)))
                .andExpect(jsonPath("$.results[*].title", not(hasItem("HiddenOmega Corp"))))
                .andExpect(jsonPath("$.results[*].subtitle", not(hasItem("HiddenOmega Corp"))));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "visibleomega")
                        .header("Authorization", "Bearer " + rep.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'ACCOUNT')].title").value(hasItem("VisibleOmega LLC")))
                .andExpect(jsonPath("$.results[?(@.type == 'LEAD')].title").value(hasItem("VisibleOmega Lead")))
                .andExpect(jsonPath("$.results[?(@.type == 'DEAL')].title").value(hasItem("VisibleOmega Deal")));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "hiddenomega")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[?(@.type == 'ACCOUNT')].title").value(hasItem("HiddenOmega Corp")));
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

    private String createLead(String token, String name, String company) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "fullName": "%s", "company": "%s", "source": "WEB" }
                                """
                                        .formatted(name, company)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createAccount(String token, String name, String industry) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "%s", "industry": "%s" }
                                """
                                        .formatted(name, industry)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void createContact(String token, String accountId, String first, String last) throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "firstName": "%s", "lastName": "%s", "accountId": "%s" }
                                """
                                        .formatted(first, last, accountId)))
                .andExpect(status().isCreated());
    }

    private void createDeal(String token, String accountId, String name) throws Exception {
        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "%s", "accountId": "%s" }
                                """
                                        .formatted(name, accountId)))
                .andExpect(status().isCreated());
    }

    private void createTask(String token, String accountId, String title) throws Exception {
        Instant due = Instant.now().plus(3, ChronoUnit.DAYS);
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "accountId": "%s", "title": "%s", "dueAt": "%s" }
                                """
                                        .formatted(accountId, title, due)))
                .andExpect(status().isCreated());
    }

    private void createMeeting(String token, String accountId, String title, Instant start) throws Exception {
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "accountId": "%s",
                                  "title": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """
                                        .formatted(accountId, title, start, start.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());
    }

    private void createCall(String token, String accountId, String title, Instant when) throws Exception {
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "accountId": "%s",
                                  "title": "%s",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND"
                                }
                                """
                                        .formatted(accountId, title, when)))
                .andExpect(status().isCreated());
    }
}
