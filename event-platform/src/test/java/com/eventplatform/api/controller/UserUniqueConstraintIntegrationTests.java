package com.eventplatform.api.controller;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.api.inmemory.InMemoryDatabase;
import com.eventplatform.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The unique indexes of the users table are the last guard: when two requests with the same email arrive
 * together, both can pass the service check, and the second insert fails on the unique constraint. The
 * in-memory repository raises the same DataIntegrityViolationException as PostgreSQL, so the API answers 409.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
class UserUniqueConstraintIntegrationTests {

    private static final String ADA = """
            {"name":"Ada", "email":"ada@example.com", "document":"%s"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDatabase database;

    @MockitoSpyBean
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        database.clear();
    }

    @Test
    void answersConflictWhenTheUniqueIndexRejectsADuplicateThatPassedTheServiceCheck() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(ADA.formatted("1")))
                .andExpect(status().isCreated());
        // The second request checked before the first one was saved.
        doReturn(false).when(userRepository).existsByEmailIgnoreCase("ada@example.com");

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(ADA.formatted("2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email or document is already registered"));
        mockMvc.perform(get("/api/users")).andExpect(jsonPath("$.length()").value(1));
    }
}
