package com.eventplatform.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** The OpenAPI definition only exists for the OWASP ZAP scan (zap profile) and always requires the JWT. */
class OpenApiDocsIntegrationTests {

    private static final String API_DOCS_PATH = "/v3/api-docs";

    private static String obtainToken(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        // Credentials from application-test.properties.
        String credentials = objectMapper.writeValueAsString(
                Map.of("username", "admin", "password", "test-admin-password"));
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("token").asString();
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @AutoConfigureMockMvc
    class DefaultProfile {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void doesNotPublishTheDefinition() throws Exception {
            mockMvc.perform(get(API_DOCS_PATH)
                            .header(HttpHeaders.AUTHORIZATION, obtainToken(mockMvc, objectMapper)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest
    // zap first, so the H2 settings of the test profile win over the PostgreSQL/Flyway settings of zap.
    @ActiveProfiles({"zap", "test"})
    @AutoConfigureMockMvc
    class ZapProfile {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get(API_DOCS_PATH)).andExpect(status().isUnauthorized());
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
                "post,   /api/auth/login",
                "get,    /api/auth/me",
                "post,   /api/users",
                "get,    /api/users",
                "get,    /api/users/{id}",
                "patch,  /api/users/{id}",
                "patch,  /api/users/{id}/status",
                "get,    /api/users/{userId}/events",
                "post,   /api/events",
                "get,    /api/events",
                "get,    /api/events/{id}",
                "put,    /api/events/{id}",
                "get,    /api/events/{id}/availability",
                "patch,  /api/events/{id}/status",
                "post,   /api/events/{eventId}/registrations",
                "get,    /api/events/{eventId}/registrations",
                "get,    /api/events/{eventId}/registrations/{userId}",
                "delete, /api/events/{eventId}/registrations/{userId}",
                "get,    /api/events/{eventId}/waitlist"
        })
        void describesEveryEndpointSoZapCanCallIt(String method, String path) throws Exception {
            mockMvc.perform(get(API_DOCS_PATH)
                            .header(HttpHeaders.AUTHORIZATION, obtainToken(mockMvc, objectMapper)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['%s'].%s", path, method).exists());
        }
    }
}
