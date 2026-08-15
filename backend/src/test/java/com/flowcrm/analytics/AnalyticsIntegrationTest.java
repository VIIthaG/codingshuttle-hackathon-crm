package com.flowcrm.analytics;

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
import com.flowcrm.enums.Role;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.notification.NotificationRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationRepository notificationRepository;

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
    void clean() {
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
    void emptyDataset_returnsZerosAndEmptyTeamForRep() throws Exception {
        String adminToken = register("an.empty.admin@example.com", "Empty Admin");
        String repToken = registerSecondAsSalesRep("an.empty.rep@example.com", "Empty Rep");

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.preset").value("LAST_30_DAYS"))
                .andExpect(jsonPath("$.leads.total").value(0))
                .andExpect(jsonPath("$.leads.created").value(0))
                .andExpect(jsonPath("$.leads.conversionRate").value(0.0))
                .andExpect(jsonPath("$.deals.openPipelineValue").value(0.0))
                .andExpect(jsonPath("$.deals.weightedPipelineValue").value(0.0))
                .andExpect(jsonPath("$.activities.tasks.overdueNow").value(0))
                .andExpect(jsonPath("$.trends.leads").isArray())
                .andExpect(jsonPath("$.team.length()").value(2));

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.length()").value(0));
    }

    @Test
    void summary_isRoleScoped_andAssigneeFilterWorks() throws Exception {
        String adminToken = register("an.scope.admin@example.com", "Scope Admin");
        String repToken = registerSecondAsSalesRep("an.scope.rep@example.com", "Scope Rep");
        String repId = meId(repToken);

        String adminLead = createLead(adminToken, "Admin Only Lead", null);
        createLead(adminToken, "Rep Visible Lead", repId);
        qualifyAndConvert(adminToken, adminLead, "Admin Convert Co");

        String adminAccount = createAccount(adminToken, "Admin Co", null);
        String repAccount = createAccount(adminToken, "Rep Co", repId);
        createDeal(adminToken, "Admin Open", adminAccount, null, "PROSPECTING", 1000, 10);
        createDeal(adminToken, "Rep Open", repAccount, repId, "PROSPECTING", 4000, 25);
        createDeal(adminToken, "Won Deal", adminAccount, null, "CLOSED_WON", 8000, 100);
        createDeal(adminToken, "Lost Deal", adminAccount, null, "CLOSED_LOST", 500, 0);

        Instant overdue = Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant future = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        String openTaskId = createTask(adminToken, adminLead, "Open overdue", overdue);
        createTask(adminToken, adminLead, "Still open", future);
        mockMvc.perform(patch("/api/v1/tasks/" + openTaskId + "/complete")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String cancelId = createTask(adminToken, adminLead, "Cancel me", future);
        cancelTask(adminToken, cancelId, adminLead, meId(adminToken), future);

        createMeeting(adminToken, adminLead, "Sync", future);
        createCall(adminToken, adminLead, "Check-in", future);

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.total").value(2))
                .andExpect(jsonPath("$.leads.created").value(2))
                .andExpect(jsonPath("$.leads.converted").value(1))
                .andExpect(jsonPath("$.leads.lost").value(0))
                .andExpect(jsonPath("$.leads.conversionRate").value(1.0))
                .andExpect(jsonPath("$.leads.byStatus[4].status").value("CONVERTED"))
                .andExpect(jsonPath("$.leads.byStatus[4].count").value(1))
                .andExpect(jsonPath("$.leads.byStatus[0].status").value("NEW"))
                .andExpect(jsonPath("$.leads.byStatus[0].count").value(1))
                .andExpect(jsonPath("$.deals.total").value(4))
                .andExpect(jsonPath("$.deals.openCount").value(2))
                .andExpect(jsonPath("$.deals.wonCount").value(1))
                .andExpect(jsonPath("$.deals.lostCount").value(1))
                .andExpect(jsonPath("$.deals.openPipelineValue").value(5000.0))
                .andExpect(jsonPath("$.deals.weightedPipelineValue").value(1100.0))
                .andExpect(jsonPath("$.deals.wonValue").value(8000.0))
                .andExpect(jsonPath("$.deals.lostValue").value(500.0))
                .andExpect(jsonPath("$.deals.averageOpenDealSize").value(2500.0))
                .andExpect(jsonPath("$.deals.byStage[0].stage").value("PROSPECTING"))
                .andExpect(jsonPath("$.deals.byStage[0].count").value(2))
                .andExpect(jsonPath("$.deals.byStage[4].stage").value("CLOSED_WON"))
                .andExpect(jsonPath("$.deals.byStage[4].totalAmount").value(8000.0))
                .andExpect(jsonPath("$.activities.tasks.created").value(3))
                .andExpect(jsonPath("$.activities.tasks.open").value(1))
                .andExpect(jsonPath("$.activities.tasks.completed").value(1))
                .andExpect(jsonPath("$.activities.tasks.cancelled").value(1))
                .andExpect(jsonPath("$.activities.tasks.overdueNow").value(0))
                .andExpect(jsonPath("$.activities.meetings.scheduled").value(1))
                .andExpect(jsonPath("$.activities.calls.planned").value(1))
                .andExpect(jsonPath("$.team.length()").value(2));

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.total").value(1))
                .andExpect(jsonPath("$.leads.converted").value(0))
                .andExpect(jsonPath("$.deals.total").value(1))
                .andExpect(jsonPath("$.deals.openPipelineValue").value(4000.0))
                .andExpect(jsonPath("$.deals.weightedPipelineValue").value(1000.0))
                .andExpect(jsonPath("$.deals.wonCount").value(0))
                .andExpect(jsonPath("$.activities.tasks.created").value(0))
                .andExpect(jsonPath("$.team.length()").value(0));

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("assignedTo", repId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.total").value(1))
                .andExpect(jsonPath("$.deals.openPipelineValue").value(4000.0))
                .andExpect(jsonPath("$.team.length()").value(1))
                .andExpect(jsonPath("$.team[0].userId").value(repId));

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + repToken)
                        .param("assignedTo", meId(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void conversionRate_usesConvertedPlusLost_andDateRangeFiltersCreated() throws Exception {
        String token = register("an.rate.admin@example.com", "Rate Admin");
        String keep = createLead(token, "Keep New", null);
        String lost = createLead(token, "Lost Lead", null);
        String convert = createLead(token, "Convert Lead", null);
        patchLeadStatus(token, lost, "LOST");
        qualifyAndConvert(token, convert, "Rate Co");

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leads.total").value(3))
                .andExpect(jsonPath("$.leads.converted").value(1))
                .andExpect(jsonPath("$.leads.lost").value(1))
                .andExpect(jsonPath("$.leads.conversionRate").value(0.5))
                .andExpect(jsonPath("$.leads.byStatus[0].count").value(1));

        Instant futureFrom = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant futureTo = Instant.now().plus(20, ChronoUnit.DAYS);
        mockMvc.perform(get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("from", futureFrom.toString())
                        .param("toExclusive", futureTo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range.preset").value("CUSTOM"))
                .andExpect(jsonPath("$.leads.total").value(3))
                .andExpect(jsonPath("$.leads.created").value(0))
                .andExpect(jsonPath("$.leads.converted").value(0))
                .andExpect(jsonPath("$.leads.conversionRate").value(0.0))
                .andExpect(jsonPath("$.deals.created").value(0));

        assert keep != null;
    }

    @Test
    void overdueNow_countsOpenPastDue_notCreatedWindow() throws Exception {
        String token = register("an.overdue.admin@example.com", "Overdue Admin");
        String leadId = createLead(token, "Overdue Owner", null);
        Instant overdue = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        createTask(token, leadId, "Late", overdue);

        mockMvc.perform(get("/api/v1/analytics/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.tasks.open").value(1))
                .andExpect(jsonPath("$.activities.tasks.overdueNow").value(1));
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

    private String registerSecondAsSalesRep(String email, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setFullName(fullName);
        user.setRole(Role.SALES_REP);
        userRepository.save(user);
        try {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "%s",
                                      "password": "password123"
                                    }
                                    """.formatted(email)))
                    .andExpect(status().isOk())
                    .andReturn();
            return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String meId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createLead(String token, String fullName, String assignedToId) throws Exception {
        String body = assignedToId == null
                ? """
                { "fullName": "%s", "source": "WEB" }
                """.formatted(fullName)
                : """
                { "fullName": "%s", "source": "WEB", "assignedToId": "%s" }
                """.formatted(fullName, assignedToId);
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void patchLeadStatus(String token, String leadId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "%s" }
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private void qualifyAndConvert(String token, String leadId, String accountName) throws Exception {
        patchLeadStatus(token, leadId, "CONTACTED");
        patchLeadStatus(token, leadId, "QUALIFIED");
        mockMvc.perform(post("/api/v1/leads/" + leadId + "/convert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "%s" }
                                """.formatted(accountName)))
                .andExpect(status().isOk());
    }

    private String createAccount(String token, String name, String ownerId) throws Exception {
        String body = ownerId == null
                ? """
                        { "name": "%s" }
                        """.formatted(name)
                : """
                        { "name": "%s", "ownerId": "%s" }
                        """.formatted(name, ownerId);
        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void createDeal(
            String token, String name, String accountId, String ownerId, String stage, int amount, int probability)
            throws Exception {
        String owner = ownerId == null ? "" : ", \"ownerId\": \"%s\"".formatted(ownerId);
        mockMvc.perform(post("/api/v1/deals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "accountId": "%s",
                                  "stage": "%s",
                                  "amount": %s,
                                  "probability": %s
                                  %s
                                }
                                """.formatted(name, accountId, stage, amount, probability, owner)))
                .andExpect(status().isCreated());
    }

    private String createTask(String token, String leadId, String title, Instant dueAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "%s",
                                  "dueAt": "%s"
                                }
                                """.formatted(leadId, title, dueAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void cancelTask(String token, String taskId, String leadId, String assignedToId, Instant dueAt)
            throws Exception {
        mockMvc.perform(put("/api/v1/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "Cancel me",
                                  "dueAt": "%s",
                                  "status": "CANCELLED"
                                }
                                """.formatted(leadId, assignedToId, dueAt)))
                .andExpect(status().isOk());
    }

    private void createMeeting(String token, String leadId, String title, Instant start) throws Exception {
        Instant end = start.plus(1, ChronoUnit.HOURS);
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "%s",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(leadId, title, start, end)))
                .andExpect(status().isCreated());
    }

    private void createCall(String token, String leadId, String title, Instant scheduledAt) throws Exception {
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "%s",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND"
                                }
                                """.formatted(leadId, title, scheduledAt)))
                .andExpect(status().isCreated());
    }
}
