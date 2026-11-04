package com.eventplatform.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Security headers that OWASP ZAP checks on every API response. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
@Transactional
class SecurityHeadersIntegrationTests {

    private static final String CROSS_ORIGIN_RESOURCE_POLICY = "Cross-Origin-Resource-Policy";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void addsCrossOriginResourcePolicyAndKeepsTheDefaultHeaders() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(header().string(CROSS_ORIGIN_RESOURCE_POLICY, "same-origin"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void addsCrossOriginResourcePolicyToCreatedAndErrorResponses() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Headers\",\"email\":\"headers-check@example.com\",\"document\":\"HEADERS-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(CROSS_ORIGIN_RESOURCE_POLICY, "same-origin"));
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CROSS_ORIGIN_RESOURCE_POLICY, "same-origin"));
        mockMvc.perform(get("/api/users").with(SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(CROSS_ORIGIN_RESOURCE_POLICY, "same-origin"));
    }

    @Test
    void stillAllowsTheFrontendCorsRequests() throws Exception {
        mockMvc.perform(get("/api/users").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(CROSS_ORIGIN_RESOURCE_POLICY, "same-origin"));
    }
}
