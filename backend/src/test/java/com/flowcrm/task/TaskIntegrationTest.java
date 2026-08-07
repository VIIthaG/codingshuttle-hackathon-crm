package com.flowcrm.task;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.lead.LeadRepository;
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
class TaskIntegrationTest {

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

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createTask_succeeds() throws Exception {
        String token = register("task.owner@example.com", "Task Owner");
        String leadId = createLead(token, "Follow-up Lead");

        Instant dueAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant reminderAt = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Call prospect",
                                  "description": "Discuss pricing",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Call prospect"))
                .andExpect(jsonPath("$.leadId").value(leadId))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedToId").isNotEmpty());
    }

    @Test
    void createTask_reminderAfterDue_returnsBadRequest() throws Exception {
        String token = register("task.invalid@example.com", "Invalid User");
        String leadId = createLead(token, "Invalid Lead");

        Instant dueAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant reminderAt = Instant.now().plus(3, ChronoUnit.DAYS);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Bad reminder",
                                  "dueAt": "%s",
                                  "reminderAt": "%s"
                                }
                                """.formatted(leadId, dueAt, reminderAt)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeTask_succeeds() throws Exception {
        String token = register("task.complete@example.com", "Complete User");
        String leadId = createLead(token, "Complete Lead");
        String taskId = createTask(token, leadId, "Finish me", Instant.now().plus(1, ChronoUnit.DAYS), null);

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminCanSeeAllTasks_salesRepOnlyOwn() throws Exception {
        String adminToken = register("task.admin@example.com", "Admin");
        JsonNode rep = registerReturningBody("task.rep@example.com", "Sales Rep");
        String repToken = rep.get("accessToken").asText();
        String repId = rep.get("user").get("id").asText();

        String adminLeadId = createLead(adminToken, "Admin Lead");
        String repLeadId = createLeadAssigned(adminToken, "Rep Lead", repId);

        String adminTaskId = createTask(adminToken, adminLeadId, "Admin task", Instant.now().plus(1, ChronoUnit.DAYS), null);
        String repTaskId = createTaskAssigned(adminToken, repLeadId, repId, "Rep task", Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(repTaskId));

        mockMvc.perform(get("/api/v1/tasks/" + adminTaskId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_forInaccessibleLead_returnsForbidden() throws Exception {
        String adminToken = register("task.leadadmin@example.com", "Lead Admin");
        String repToken = register("task.leadrep@example.com", "Lead Rep");
        String leadId = createLead(adminToken, "Private Lead");

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "Should fail",
                                  "dueAt": "%s"
                                }
                                """.formatted(leadId, Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listTasks_filterByStatus() throws Exception {
        String token = register("task.filter@example.com", "Filter User");
        String leadId = createLead(token, "Filter Lead");
        String openId = createTask(token, leadId, "Open task", Instant.now().plus(1, ChronoUnit.DAYS), null);
        String doneId = createTask(token, leadId, "Done task", Instant.now().plus(1, ChronoUnit.DAYS), null);

        mockMvc.perform(patch("/api/v1/tasks/" + doneId + "/complete")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(openId));

        mockMvc.perform(get("/api/v1/tasks")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(doneId));
    }

    @Test
    void listTasks_filterOverdue() throws Exception {
        String token = register("task.overdue@example.com", "Overdue User");
        String leadId = createLead(token, "Overdue Lead");
        String overdueId = createTask(token, leadId, "Overdue task", Instant.now().minus(1, ChronoUnit.DAYS), null);
        createTask(token, leadId, "Future task", Instant.now().plus(2, ChronoUnit.DAYS), null);

        mockMvc.perform(get("/api/v1/tasks")
                        .param("overdue", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(overdueId));
    }

    private String createLead(String token, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s@example.com",
                                  "source": "WEB"
                                }
                                """.formatted(fullName, fullName.replace(" ", "").toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createLeadAssigned(String token, String fullName, String assignedToId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s@example.com",
                                  "source": "WEB",
                                  "assignedToId": "%s"
                                }
                                """.formatted(fullName, fullName.replace(" ", "").toLowerCase(), assignedToId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTask(String token, String leadId, String title, Instant dueAt, Instant reminderAt)
            throws Exception {
        String reminderJson = reminderAt == null ? "" : ", \"reminderAt\": \"%s\"".formatted(reminderAt);
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "title": "%s",
                                  "dueAt": "%s"%s
                                }
                                """.formatted(leadId, title, dueAt, reminderJson)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createTaskAssigned(
            String token, String leadId, String assignedToId, String title, Instant dueAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leadId": "%s",
                                  "assignedToId": "%s",
                                  "title": "%s",
                                  "dueAt": "%s"
                                }
                                """.formatted(leadId, assignedToId, title, dueAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String register(String email, String fullName) throws Exception {
        return registerReturningBody(email, fullName).get("accessToken").asText();
    }

    private JsonNode registerReturningBody(String email, String fullName) throws Exception {
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
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
