package com.flowcrm.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.cors.allowed-origins=https://flowcrm.up.railway.app")
class CorsProductionOriginIntegrationTest {

    private static final String PRODUCTION_ORIGIN = "https://flowcrm.up.railway.app";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @Test
    void corsConfigurationSourceBean_isUrlBasedAndRegistered() {
        org.assertj.core.api.Assertions.assertThat(corsConfigurationSource).isNotNull();
        org.assertj.core.api.Assertions.assertThat(corsConfigurationSource.getCorsConfigurations())
                .containsKey("/**");
    }

    @Test
    void optionsPreflight_toRegister_fromProductionOrigin_succeeds() throws Exception {
        mockMvc.perform(options("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, PRODUCTION_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PRODUCTION_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    void optionsPreflight_fromUnapprovedOrigin_doesNotAllowOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflight_fromLocalhost_whenOnlyProductionConfigured_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
