package com.flowcrm.lead;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class LeadPipelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeadRepository leadRepository;

    @BeforeEach
    void cleanDatabase() {
        leadRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void changeStatus_successfulTransition() throws Exception {
        String token = registerAndGetToken("pipeline.owner@example.com", "Pipeline Owner");
        String leadId = createLead(token, "Pipeline Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONTACTED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(leadId))
                .andExpect(jsonPath("$.status").value("CONTACTED"));

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "QUALIFIED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));
    }

    @Test
    void listLeads_filterByStatus() throws Exception {
        String token = registerAndGetToken("pipeline.filter@example.com", "Filter User");
        String newLeadId = createLead(token, "New Lead", "NEW");
        String contactedLeadId = createLead(token, "Contacted Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + contactedLeadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONTACTED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/leads")
                        .param("status", "CONTACTED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(contactedLeadId))
                .andExpect(jsonPath("$.content[0].status").value("CONTACTED"));

        mockMvc.perform(get("/api/v1/leads")
                        .param("status", "NEW")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newLeadId));
    }

    @Test
    void changeStatus_unassignedLead_returnsForbidden() throws Exception {
        String ownerToken = registerAndGetToken("pipeline.admin@example.com", "Admin Owner");
        String otherToken = registerAndGetToken("pipeline.rep@example.com", "Sales Rep");
        String leadId = createLead(ownerToken, "Owned Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONTACTED" }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/leads/" + leadId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_withoutToken_returnsUnauthorized() throws Exception {
        String token = registerAndGetToken("pipeline.auth@example.com", "Auth User");
        String leadId = createLead(token, "Auth Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONTACTED" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeStatus_invalidTransition_returnsBadRequest() throws Exception {
        String token = registerAndGetToken("pipeline.invalid@example.com", "Invalid User");
        String leadId = createLead(token, "Invalid Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "CONVERTED" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Status Transition"));

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "LOST" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOST"));

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "NEW" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeStatus_missingStatus_returnsBadRequest() throws Exception {
        String token = registerAndGetToken("pipeline.validation@example.com", "Validation User");
        String leadId = createLead(token, "Validation Lead", "NEW");

        mockMvc.perform(patch("/api/v1/leads/" + leadId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private String createLead(String token, String fullName, String status) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s@example.com",
                                  "source": "WEB",
                                  "status": "%s"
                                }
                                """.formatted(fullName, fullName.replace(" ", "").toLowerCase(), status)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String registerAndGetToken(String email, String fullName) throws Exception {
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
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
