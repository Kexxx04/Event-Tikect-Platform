package com.eventplatform.api.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.api.inmemory.InMemoryDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/** Only GET /actuator/health is open, for the Docker healthcheck; the rest of /actuator stays closed. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ActuatorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDatabase database;

    @BeforeEach
    void clearDatabase() {
        database.clear();
    }

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() throws Exception {
        mockMvc.perform(get(SecurityConfig.HEALTH_PATH))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator",
            "/actuator/info",
            "/actuator/env",
            "/actuator/beans",
            "/actuator/configprops",
            "/actuator/metrics",
            "/actuator/loggers",
            "/actuator/heapdump",
            "/actuator/threaddump",
            "/actuator/health/diskSpace",
            "/actuator/health/ping"
    })
    void otherActuatorEndpointsNeedATokenAndAreNotExposed(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).with(user("admin"))).andExpect(status().isNotFound());
    }

    @Test
    void healthOnlyAcceptsGetWithoutAToken() throws Exception {
        mockMvc.perform(post(SecurityConfig.HEALTH_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/actuator/shutdown")).andExpect(status().isUnauthorized());
    }
}
