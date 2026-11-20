package com.eventplatform.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
@Transactional
class RegistrationControllerIntegrationTests {

    private static final String EVENT = """
            {"name":"Java Conf", "description":"Talks", "startDate":"2030-05-10T18:00:00",
             "endDate":"2030-05-10T21:00:00", "capacity":2}
            """;

    private static final long MISSING_ID = 9223372036854775807L;

    @Autowired
    private MockMvc mockMvc;

    private long createEvent(String body) throws Exception {
        return idOf(mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location"));
    }

    private long createEvent() throws Exception {
        return createEvent(EVENT);
    }

    private long createUser(String document) throws Exception {
        String body = "{\"name\":\"User " + document + "\",\"email\":\"" + document
                + "@example.com\",\"document\":\"" + document + "\"}";
        return idOf(mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location"));
    }

    private ResultActions register(long eventId, long userId) throws Exception {
        return mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + "}"));
    }

    private ResultActions cancel(long eventId, long userId) throws Exception {
        return mockMvc.perform(delete("/api/events/" + eventId + "/registrations/" + userId));
    }

    private void changeEventStatus(long eventId, String newStatus) throws Exception {
        mockMvc.perform(patch("/api/events/" + eventId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + newStatus + "\"}"))
                .andExpect(status().isOk());
    }

    private long idOf(String location) {
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void confirmsARegistrationAndCountsTheSeat() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");

        register(eventId, userId)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/events/" + eventId + "/registrations/" + userId))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.eventName").value("Java Conf"))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.userName").value("User 1001"))
                .andExpect(jsonPath("$.userEmail").value("1001@example.com"))
                .andExpect(jsonPath("$.registrationDate").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(1));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void waitlistsRegistrationsOnceTheEventIsFull() throws Exception {
        long eventId = createEvent();
        register(eventId, createUser("1001")).andExpect(jsonPath("$.status").value("CONFIRMED"));
        register(eventId, createUser("1002")).andExpect(jsonPath("$.status").value("CONFIRMED"));
        long third = createUser("1003");
        long fourth = createUser("1004");

        register(eventId, third).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITING_LIST"));
        register(eventId, fourth).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITING_LIST"));

        mockMvc.perform(get("/api/events/" + eventId + "/availability"))
                .andExpect(jsonPath("$.registeredCount").value(2))
                .andExpect(jsonPath("$.availableSeats").value(0))
                .andExpect(jsonPath("$.waitlistCount").value(2));
        mockMvc.perform(get("/api/events/" + eventId + "/waitlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].userId").value(third))
                .andExpect(jsonPath("$[1].position").value(2))
                .andExpect(jsonPath("$[1].userId").value(fourth));
    }

    @Test
    void cancellingAConfirmedRegistrationPromotesTheFirstWaitlistedUser() throws Exception {
        long eventId = createEvent();
        long first = createUser("1001");
        long second = createUser("1002");
        long waitlisted = createUser("1003");
        register(eventId, first);
        register(eventId, second);
        register(eventId, waitlisted);

        cancel(eventId, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.userId").value(first))
                .andExpect(jsonPath("$.cancelled.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted.userId").value(waitlisted))
                .andExpect(jsonPath("$.promoted.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(2));
        mockMvc.perform(get("/api/events/" + eventId + "/waitlist")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(second))
                .andExpect(jsonPath("$[1].userId").value(waitlisted));
    }

    @Test
    void cancellingWithoutWaitlistReleasesTheSeat() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        register(eventId, userId);

        cancel(eventId, userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted").doesNotExist());

        mockMvc.perform(get("/api/events/" + eventId + "/availability"))
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.availableSeats").value(2));
    }

    @Test
    void cancellingAWaitlistedRegistrationKeepsTheEventFull() throws Exception {
        long eventId = createEvent();
        register(eventId, createUser("1001"));
        register(eventId, createUser("1002"));
        long waitlisted = createUser("1003");
        register(eventId, waitlisted);

        cancel(eventId, waitlisted)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted").doesNotExist());

        mockMvc.perform(get("/api/events/" + eventId + "/availability"))
                .andExpect(jsonPath("$.registeredCount").value(2))
                .andExpect(jsonPath("$.waitlistCount").value(0));
    }

    @Test
    void rejectsCancellingTwice() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        register(eventId, userId);
        cancel(eventId, userId).andExpect(status().isOk());

        cancel(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Registration is already cancelled"));
        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(0));
    }

    @Test
    void aUserCanRegisterAgainAfterCancelling() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        register(eventId, userId);
        cancel(eventId, userId);

        register(eventId, userId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(1));
    }

    @Test
    void rejectsADuplicateRegistration() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        register(eventId, userId);

        register(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User is already registered for this event"))
                .andExpect(jsonPath("$.path").value("/api/events/" + eventId + "/registrations"));
        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(1));
    }

    @Test
    void rejectsInactiveUsers() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        mockMvc.perform(patch("/api/users/" + userId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());

        register(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Inactive users cannot register for events"));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations")).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLED", "FINISHED"})
    void rejectsRegistrationsForClosedEvents(String closedStatus) throws Exception {
        long eventId = createEvent();
        changeEventStatus(eventId, "ACTIVE");
        changeEventStatus(eventId, closedStatus);

        register(eventId, createUser("1001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event with status " + closedStatus
                        + " does not accept registrations"));
    }

    @Test
    void rejectsRegistrationsAfterTheEventHasStarted() throws Exception {
        long eventId = createEvent(EVENT.replace("2030-05-10T18:00:00", "2020-01-01T09:00:00"));

        register(eventId, createUser("1001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Registrations are closed because the event has already started"));
        mockMvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.registeredCount").value(0));
    }

    @Test
    void rejectsCancellingRegistrationsOfAFinishedEvent() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");
        register(eventId, userId);
        changeEventStatus(eventId, "ACTIVE");
        changeEventStatus(eventId, "FINISHED");

        cancel(eventId, userId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Registrations of an event with status FINISHED cannot be cancelled"));
    }

    @Test
    void returnsNotFoundForUnknownEventsUsersAndRegistrations() throws Exception {
        long eventId = createEvent();
        long userId = createUser("1001");

        register(MISSING_ID, userId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event with id " + MISSING_ID + " was not found"));
        register(eventId, MISSING_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with id " + MISSING_ID + " was not found"));
        cancel(eventId, userId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Registration of user " + userId + " for event " + eventId
                        + " was not found"));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations/" + userId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/" + MISSING_ID + "/registrations")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/" + MISSING_ID + "/waitlist")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + MISSING_ID + "/events")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"userId\":null}", "{\"userId\":0}", "{\"userId\":-5}"})
    void rejectsInvalidRegistrationRequests(String body) throws Exception {
        long eventId = createEvent();
        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.userId").isNotEmpty());
    }

    @Test
    void rejectsMalformedRequestsAndParameters() throws Exception {
        long eventId = createEvent();
        mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
        mockMvc.perform(get("/api/events/abc/registrations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'eventId'"));
    }

    @Test
    void listsTheRegistrationsOfAnEventByStatus() throws Exception {
        long eventId = createEvent();
        long first = createUser("1001");
        long second = createUser("1002");
        long third = createUser("1003");
        register(eventId, first);
        register(eventId, second);
        register(eventId, third);
        cancel(eventId, second);

        mockMvc.perform(get("/api/events/" + eventId + "/registrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(first))
                .andExpect(jsonPath("$[1].userId").value(third));
        mockMvc.perform(get("/api/events/" + eventId + "/registrations").param("status", "CANCELLED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(second));
    }

    @Test
    void listsTheEventsOfAUserByStartDate() throws Exception {
        long later = createEvent(EVENT.replace("Java Conf", "Later Conf")
                .replace("2030-05-10T18:00:00", "2031-01-10T18:00:00")
                .replace("2030-05-10T21:00:00", "2031-01-10T21:00:00"));
        long sooner = createEvent(EVENT.replace("Java Conf", "Sooner Conf"));
        long other = createEvent(EVENT.replace("Java Conf", "Other Conf"));
        long userId = createUser("1001");
        register(later, userId);
        register(sooner, userId);
        register(other, createUser("1002"));

        mockMvc.perform(get("/api/users/" + userId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventId").value(sooner))
                .andExpect(jsonPath("$[0].eventName").value("Sooner Conf"))
                .andExpect(jsonPath("$[0].startDate").value("2030-05-10T18:00:00"))
                .andExpect(jsonPath("$[0].endDate").value("2030-05-10T21:00:00"))
                .andExpect(jsonPath("$[0].eventStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].registrationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].registrationDate").isNotEmpty())
                .andExpect(jsonPath("$[1].eventId").value(later));

        cancel(later, userId);
        mockMvc.perform(get("/api/users/" + userId + "/events").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value(sooner));
    }
}
