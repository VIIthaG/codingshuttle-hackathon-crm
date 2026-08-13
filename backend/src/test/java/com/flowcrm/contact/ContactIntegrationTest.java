package com.flowcrm.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowcrm.account.AccountRepository;
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
class ContactIntegrationTest {

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
    void createContact_withAndWithoutAccount() throws Exception {
        String token = register("ct.owner@example.com", "Contact Owner");
        String accountId = createAccount(token, "Linked Co");

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Jane",
                                  "lastName": "Doe",
                                  "email": "Jane@Linked.example",
                                  "phone": "555-0100",
                                  "jobTitle": "Buyer",
                                  "accountId": "%s"
                                }
                                """.formatted(accountId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.email").value("jane@linked.example"))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.accountName").value("Linked Co"));

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Sam",
                                  "lastName": "Solo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isEmpty())
                .andExpect(jsonPath("$.accountName").isEmpty());

        mockMvc.perform(get("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactCount").value(1));
    }

    @Test
    void listGetUpdateDeleteContact_andAccountFilter() throws Exception {
        String token = register("ct.crud@example.com", "CRUD");
        String accountA = createAccount(token, "Alpha");
        String accountB = createAccount(token, "Beta");
        String contactA = createContact(token, "Ann", "Alpha", accountA);
        createContact(token, "Bob", "Beta", accountB);

        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", accountA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(contactA));

        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .param("search", "ann"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/contacts/" + contactA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ann"));

        String ownerId = meId(token);
        mockMvc.perform(put("/api/v1/contacts/" + contactA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Ann",
                                  "lastName": "Updated",
                                  "email": "ann@alpha.example",
                                  "phone": "555-1",
                                  "jobTitle": "VP",
                                  "notes": "Key contact",
                                  "accountId": "%s",
                                  "ownerId": "%s"
                                }
                                """.formatted(accountA, ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.jobTitle").value("VP"));

        mockMvc.perform(delete("/api/v1/contacts/" + contactA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/contacts/" + contactA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createContact_missingName_returnsBadRequest() throws Exception {
        String token = register("ct.val@example.com", "Val");

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Only" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesRep_cannotSeeOrMutateAnotherUsersContact() throws Exception {
        String adminToken = register("ct.admin@example.com", "Admin");
        String repToken = register("ct.rep@example.com", "Rep");
        String contactId = createContact(adminToken, "Hidden", "Person", null);

        mockMvc.perform(get("/api/v1/contacts/" + contactId)
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", "Bearer " + repToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void admin_canAssignContactOwner() throws Exception {
        String adminToken = register("ct.assign.admin@example.com", "Admin");
        String repToken = register("ct.assign.rep@example.com", "Rep");
        String repId = meId(repToken);

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Pat",
                                  "lastName": "Assigned",
                                  "ownerId": "%s"
                                }
                                """.formatted(repId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(repId));

        mockMvc.perform(get("/api/v1/contacts")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("ownerId", repId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void salesRep_cannotAssignContactToAnotherUser() throws Exception {
        String adminToken = register("ct.noassign.admin@example.com", "Admin");
        String repToken = register("ct.noassign.rep@example.com", "Rep");
        String adminId = meId(adminToken);

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "No",
                                  "lastName": "Assign",
                                  "ownerId": "%s"
                                }
                                """.formatted(adminId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createContact_sameKeySameBody_replays() throws Exception {
        String token = register("ct.idem.replay@example.com", "Replay");
        String key = "ct-key-1";
        String body = """
                { "firstName": "Idem", "lastName": "Contact" }
                """;

        MvcResult first = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(contactRepository.count()).isEqualTo(1);
    }

    @Test
    void createContact_sameKeyDifferentBody_returns409() throws Exception {
        String token = register("ct.idem.conflict@example.com", "Conflict");
        String key = "ct-key-conflict";

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "First", "lastName": "Body" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "firstName": "Second", "lastName": "Body" }
                                """))
                .andExpect(status().isConflict());

        assertThat(contactRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingAccount_unlinksContact() throws Exception {
        String token = register("ct.unlink@example.com", "Unlink");
        String accountId = createAccount(token, "Gone Co");
        String contactId = createContact(token, "Keep", "Me", accountId);

        mockMvc.perform(delete("/api/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/contacts/" + contactId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").isEmpty());
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
        String body = accountId == null
                ? """
                        { "firstName": "%s", "lastName": "%s" }
                        """.formatted(first, last)
                : """
                        { "firstName": "%s", "lastName": "%s", "accountId": "%s" }
                        """.formatted(first, last, accountId);
        MvcResult result = mockMvc.perform(post("/api/v1/contacts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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
