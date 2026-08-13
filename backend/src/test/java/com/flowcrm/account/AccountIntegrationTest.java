package com.flowcrm.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AccountIntegrationTest {

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
        taskRepository.deleteAll();
        leadRepository.deleteAll();
        contactRepository.deleteAll();
        accountRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createAccount_requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Acme" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createGetListUpdateDeleteAccount_succeeds() throws Exception {
        String token = register("acct.crud@example.com", "Acct Owner");

        MvcResult createResult = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Corp",
                                  "website": "https://acme.example",
                                  "phone": "555-0100",
                                  "industry": "Software",
                                  "description": "Enterprise customer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.website").value("https://acme.example"))
                .andExpect(jsonPath("$.industry").value("Software"))
                .andExpect(jsonPath("$.ownerId").isNotEmpty())
                .andExpect(jsonPath("$.ownerName").value("Acct Owner"))
                .andExpect(jsonPath("$.contactCount").value(0))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String accountId = created.get("id").asText();
        String ownerId = created.get("ownerId").asText();

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(put("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Updated",
                                  "website": "https://acme.example",
                                  "phone": "555-9999",
                                  "industry": "SaaS",
                                  "description": "Updated",
                                  "ownerId": "%s"
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Updated"))
                .andExpect(jsonPath("$.industry").value("SaaS"));

        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAccount_missingName_returnsBadRequest() throws Exception {
        String token = register("acct.val@example.com", "Val User");

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "website": "https://x.example" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesRep_cannotSeeOrMutateAnotherUsersAccount() throws Exception {
        String adminToken = register("acct.admin@example.com", "Admin");
        String repToken = register("acct.rep@example.com", "Rep");
        String accountId = createAccount(adminToken, "Admin Co");

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canListAllAndAssignOwner() throws Exception {
        String adminToken = register("acct.assign.admin@example.com", "Assign Admin");
        String repToken = register("acct.assign.rep@example.com", "Assign Rep");
        String repId = meId(repToken);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());

        MvcResult create = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Assigned Co",
                                  "ownerId": "%s"
                                }
                                """.formatted(repId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(repId))
                .andReturn();

        String accountId = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("ownerId", repId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(accountId));

        mockMvc.perform(get("/api/v1/accounts")
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void salesRep_cannotAssignAccountToAnotherUser() throws Exception {
        String adminToken = register("acct.noassign.admin@example.com", "Admin");
        String repToken = register("acct.noassign.rep@example.com", "Rep");
        String adminId = meId(adminToken);

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Rep Co",
                                  "ownerId": "%s"
                                }
                                """.formatted(adminId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createAccount_sameKeySameBody_replays() throws Exception {
        String token = register("acct.idem.replay@example.com", "Replay");
        String key = "acct-key-1";
        String body = """
                { "name": "Replay Co", "industry": "Media" }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.count()).isEqualTo(1);
    }

    @Test
    void createAccount_sameKeyDifferentBody_returns409() throws Exception {
        String token = register("acct.idem.conflict@example.com", "Conflict");
        String key = "acct-key-conflict";

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "First Co" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Second Co" }
                                """))
                .andExpect(status().isConflict());

        assertThat(accountRepository.count()).isEqualTo(1);
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
