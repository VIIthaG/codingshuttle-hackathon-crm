package com.flowcrm.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.deal.DealRepository;
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
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void apiDocs_areAccessibleWithoutJwt() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FlowCRM API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"bearerAuth\"");
        assertThat(body).contains("\"type\":\"http\"");
        assertThat(body).contains("\"scheme\":\"bearer\"");
        assertThat(body).contains("JWT");
        assertThat(body).contains("Idempotency-Key");
        assertThat(body).contains("Authentication");
        assertThat(body).contains("Accounts");
        assertThat(body).contains("Contacts");
        assertThat(body).contains("Deals");
        assertThat(body).contains("Users");
        assertThat(body).contains("Leads");
        assertThat(body).contains("Tasks");
        assertThat(body).contains("Meetings");
        assertThat(body).contains("Calls");
        assertThat(body).contains("Calendar");
        assertThat(body).contains("Workqueue");
        assertThat(body).contains("Search");
        assertThat(body).contains("Notifications");
        assertThat(body).contains("Activities");
        assertThat(body).contains("Analytics");
        assertThat(body).contains("Dashboard");
        assertThat(body).contains("Health");
    }

    @Test
    void swaggerUi_isAccessibleWithoutJwt() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isFound());

        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void businessEndpoints_remainProtectedWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/contacts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/deals")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/meetings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/calls")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/calendar")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workqueue")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/search").param("q", "ac")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/activities/timeline")
                        .param("entityType", "LEAD")
                        .param("entityId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Blocked",
                                  "source": "WEB"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/leads/00000000-0000-0000-0000-000000000001/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "accountName": "Blocked" }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
