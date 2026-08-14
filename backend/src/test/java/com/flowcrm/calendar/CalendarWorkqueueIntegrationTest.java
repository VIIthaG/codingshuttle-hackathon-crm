package com.flowcrm.calendar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
class CalendarWorkqueueIntegrationTest {

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
    void calendar_includesOpenTaskScheduledMeetingPlannedCall_excludesCompleted() throws Exception {
        String token = register("cal.admin@example.com", "Cal Admin");
        String leadId = createLead(token, "Cal Lead");
        Instant now = Instant.now();
        Instant due = now.plus(2, ChronoUnit.HOURS);
        Instant start = now.plus(3, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        Instant callAt = now.plus(4, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "Send pricing", "dueAt": "%s" }
                                """.formatted(leadId, due)))
                .andExpect(status().isCreated());
        String meetingId = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/meetings")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "leadId": "%s",
                                          "title": "Discovery",
                                          "startAt": "%s",
                                          "endAt": "%s"
                                        }
                                        """.formatted(leadId, start, end)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText();
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Follow up",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND"
                                }
                                """.formatted(leadId, callAt)))
                .andExpect(status().isCreated());

        Instant from = now.minus(1, ChronoUnit.HOURS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        mockMvc.perform(get("/api/v1/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemType == 'TASK')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.itemType == 'MEETING')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.itemType == 'CALL')]").isNotEmpty());

        mockMvc.perform(patch("/api/v1/meetings/" + meetingId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "COMPLETED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/calendar")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemType == 'MEETING')]").isEmpty());
    }

    @Test
    void workqueue_sections_andSalesRepScope() throws Exception {
        String adminToken = register("wq.admin@example.com", "Admin");
        String repToken = register("wq.rep@example.com", "Rep");
        String adminLead = createLead(adminToken, "Admin Lead");
        String repLead = createLead(repToken, "Rep Lead");

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant overdueDue = startOfToday.minus(2, ChronoUnit.HOURS);
        Instant todayDue = startOfToday.plus(10, ChronoUnit.HOURS);
        Instant upcomingDue = startOfToday.plus(3, ChronoUnit.DAYS);
        Instant todayMeetingStart = startOfToday.plus(11, ChronoUnit.HOURS);
        Instant upcomingMeetingStart = startOfToday.plus(2, ChronoUnit.DAYS);
        Instant todayCall = startOfToday.plus(12, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "Overdue", "dueAt": "%s" }
                                """.formatted(repLead, overdueDue)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "Today task", "dueAt": "%s" }
                                """.formatted(repLead, todayDue)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "Later task", "dueAt": "%s" }
                                """.formatted(repLead, upcomingDue)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Today meeting",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(repLead, todayMeetingStart, todayMeetingStart.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/meetings")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Later meeting",
                                  "startAt": "%s",
                                  "endAt": "%s"
                                }
                                """.formatted(
                                repLead, upcomingMeetingStart, upcomingMeetingStart.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/calls")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Today call",
                                  "scheduledAt": "%s",
                                  "direction": "OUTBOUND"
                                }
                                """.formatted(repLead, todayCall)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "leadId": "%s", "title": "Admin overdue", "dueAt": "%s" }
                                """.formatted(adminLead, overdueDue)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/workqueue").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueTasks[0].title").value("Overdue"))
                .andExpect(jsonPath("$.dueTodayTasks[0].title").value("Today task"))
                .andExpect(jsonPath("$.upcomingTasks[0].title").value("Later task"))
                .andExpect(jsonPath("$.todayMeetings[0].title").value("Today meeting"))
                .andExpect(jsonPath("$.upcomingMeetings[0].title").value("Later meeting"))
                .andExpect(jsonPath("$.todayCalls[0].title").value("Today call"))
                .andExpect(jsonPath("$.overdueTasks.length()").value(1));

        mockMvc.perform(get("/api/v1/workqueue").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueTasks.length()").value(2));

        mockMvc.perform(get("/api/v1/calendar")
                        .param("from", startOfToday.toString())
                        .param("to", startOfToday.plus(14, ChronoUnit.DAYS).toString())
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'Admin overdue')]").isEmpty());
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
