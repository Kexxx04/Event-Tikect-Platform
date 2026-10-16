package com.eventplatform.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private String createUser(String email, String document) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ada", "email":"%s", "document":"%s"}
                                """.formatted(email, document)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    @Test
    void persistsPartialUpdatesAndAllowsKeepingOwnUniqueFields() throws Exception {
        String location = createUser("ada@example.com", "12345");
        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Ada Byron  ", "email":"ADA@EXAMPLE.COM", "document":"12345"}
                                """))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Byron"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.document").value("12345"));
        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.document").value("12345"));
    }

    @Test
    void persistsStatusChanges() throws Exception {
        String location = createUser("ada@example.com", "12345");
        for (String state : new String[]{"INACTIVE", "ACTIVE"}) {
            mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + state + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state));
            entityManager.flush();
            entityManager.clear();
            mockMvc.perform(get(location)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(state));
        }
    }

    @Test
    void listsUsersInCreationOrder() throws Exception {
        createUser("first@example.com", "FIRST");
        createUser("second@example.com", "SECOND");
        mockMvc.perform(get("/api/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("first@example.com"))
                .andExpect(jsonPath("$[1].email").value("second@example.com"));
    }

    @Test
    void returnsNotFoundForMissingUsers() throws Exception {
        mockMvc.perform(get("/api/users/9223372036854775807")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/9223372036854775807").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/9223372036854775807/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"Ada\",\"email\":\"invalid\",\"document\":\"123\"}",
            "{\"name\":\" \",\"email\":\"ada@example.com\",\"document\":\"123\"}"})
    void rejectsInvalidCreationData(String body) throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\"   \"}", "{\"email\":\"invalid\"}", "{\"document\":\"   \"}"})
    void rejectsInvalidUpdatesWithoutChangingUser(String body) throws Exception {
        String location = createUser("ada@example.com", "12345");
        mockMvc.perform(patch(location).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.document").value("12345"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}"})
    void rejectsInvalidStatuses(String body) throws Exception {
        String location = createUser("ada@example.com", "12345");
        mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(location)).andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectsDuplicateDocumentsAndConflictingUpdates() throws Exception {
        createUser("first@example.com", "FIRST");
        String second = createUser("second@example.com", "SECOND");
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Third\",\"email\":\"third@example.com\",\"document\":\"FIRST\"}"))
                .andExpect(status().isConflict());
        for (String body : new String[]{"{\"email\":\"FIRST@EXAMPLE.COM\"}", "{\"document\":\"FIRST\"}"}) {
            mockMvc.perform(patch(second).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
        }
        mockMvc.perform(get(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("second@example.com"))
                .andExpect(jsonPath("$.document").value("SECOND"));
    }

    @Test
    void createsAndRetrievesAUser() throws Exception {
        String uniqueValue = UUID.randomUUID().toString();
        String email = "ada-%s@example.com".formatted(uniqueValue);
        String document = "DOC-%s".formatted(uniqueValue);
        String body = """
                {
                  "name": "Ada Lovelace",
                  "email": "%s",
                  "document": "%s"
                }
                """.formatted(email, document);

        String location = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.document").value(document))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.document").value(document));
    }

    @Test
    void rejectsADuplicatedEmail() throws Exception {
        String uniqueValue = UUID.randomUUID().toString();
        String email = "duplicate-%s@example.com".formatted(uniqueValue);
        String firstUser = """
                {
                  "name": "First User",
                  "email": "%s",
                  "document": "DOC-1-%s"
                }
                """.formatted(email, uniqueValue);
        String secondUser = """
                {
                  "name": "Second User",
                  "email": "%s",
                  "document": "DOC-2-%s"
                }
                """.formatted(email.toUpperCase(), uniqueValue);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstUser))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }
}
