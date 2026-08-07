package com.flowcrm.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.config.CacheConfig;
import com.flowcrm.dashboard.dto.DashboardSummaryResponse;
import com.flowcrm.enums.LeadStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.user.User;
import com.flowcrm.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private LeadRepository leadRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        processedMessageRepository.deleteAll();
        outboxEventRepository.deleteAll();
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        userRepository.deleteAll();
        Cache cache = cacheManager.getCache(CacheConfig.DASHBOARD_SUMMARY_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void adminSeesGlobalAggregates_salesRepSeesOnlyAssigned() throws Exception {
        String adminToken = register("dash.admin@example.com", "Dash Admin");
        String repToken = registerSecondAsSalesRep("dash.rep@example.com", "Dash Rep");

        createLead(adminToken, "Admin Lead", null);
        String repUserId = meId(repToken);
        createLead(adminToken, "Rep Lead", repUserId);

        Instant dueAt = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        Instant reminderAt = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        String adminLeadId = createLead(adminToken, "Task Lead", null);
        createTask(adminToken, adminLeadId, dueAt, reminderAt);

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(3))
                .andExpect(jsonPath("$.leadsByStatus.NEW").value(3))
                .andExpect(jsonPath("$.openTasks").value(1))
                .andExpect(jsonPath("$.upcomingFollowUps").value(1));

        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(1))
                .andExpect(jsonPath("$.leadsByStatus.NEW").value(1))
                .andExpect(jsonPath("$.openTasks").value(0))
                .andExpect(jsonPath("$.upcomingFollowUps").value(0));
    }

    @Test
    void cacheHitThenInvalidationOnLeadMutation() throws Exception {
        String token = register("dash.cache@example.com", "Dash Cache");
        UserPrincipal principal = principalForEmail("dash.cache@example.com");

        var first = dashboardService.getSummary(principal);
        assertThat(first.totalLeads()).isZero();

        Cache cache = cacheManager.getCache(CacheConfig.DASHBOARD_SUMMARY_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get(principal.getId())).isNotNull();

        clearInvocations(leadRepository);
        assertThat(dashboardService.getSummary(principal).totalLeads()).isZero();
        verify(leadRepository, times(0)).count();

        createLead(token, "Invalidate Lead", null);

        assertThat(cache.get(principal.getId())).isNull();

        var after = dashboardService.getSummary(principal);
        assertThat(after.totalLeads()).isEqualTo(1);
        assertThat(after.leadsByStatus().get(LeadStatus.NEW)).isEqualTo(1L);
        verify(leadRepository, atLeastOnce()).count();
    }

    @Test
    void redisValueSerializerRoundTrip_isHttpWritableWithoutRecomputeSemantics() throws Exception {
        // Same serializer production Redis cache uses — proves write/read type restoration.
        RedisSerializer<DashboardSummaryResponse> serializer = CacheConfig.dashboardSummaryRedisSerializer();
        DashboardSummaryResponse computed = DashboardSummaryResponse.of(
                1,
                Map.of(
                        LeadStatus.NEW, 0L,
                        LeadStatus.CONTACTED, 1L,
                        LeadStatus.QUALIFIED, 0L,
                        LeadStatus.CONVERTED, 0L,
                        LeadStatus.LOST, 0L),
                4,
                2,
                1);

        DashboardSummaryResponse fromCache = serializer.deserialize(serializer.serialize(computed));
        assertThat(fromCache).isEqualTo(computed);
        assertThat(objectMapper.writeValueAsString(fromCache)).contains("\"CONTACTED\":1");
    }

    private UserPrincipal principalForEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        return new UserPrincipal(user);
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
                {
                  "fullName": "%s",
                  "email": "%s@example.com",
                  "source": "WEB"
                }
                """.formatted(fullName, fullName.replace(" ", "").toLowerCase())
                : """
                {
                  "fullName": "%s",
                  "email": "%s@example.com",
                  "source": "WEB",
                  "assignedToId": "%s"
                }
                """.formatted(fullName, fullName.replace(" ", "").toLowerCase(), assignedToId);

        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void createTask(String token, String leadId, Instant dueAt, Instant reminderAt) throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Follow up",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated());
    }
}
