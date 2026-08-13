package com.flowcrm.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowcrm.account.AccountRepository;
import com.flowcrm.contact.ContactRepository;
import com.flowcrm.idempotency.IdempotencyRecordRepository;
import com.flowcrm.lead.LeadRepository;
import com.flowcrm.outbox.OutboxEventRepository;
import com.flowcrm.reminder.ProcessedMessageRepository;
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
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

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
        taskRepository.deleteAll();
        leadRepository.deleteAll();
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
        assertThat(body).contains("Users");
        assertThat(body).contains("Leads");
        assertThat(body).contains("Tasks");
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
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isUnauthorized());
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
    }
}
