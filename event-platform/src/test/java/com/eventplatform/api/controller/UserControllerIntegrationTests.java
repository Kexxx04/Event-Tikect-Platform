package com.eventplatform.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
