package com.eventplatform.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.RegistrationRepository;
import com.eventplatform.api.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
@Transactional
class EventControllerIntegrationTests {

    private static final String VALID_EVENT = """
            {"name":"Java Conf", "description":"Talks", "startDate":"2030-05-10T18:00:00",
             "endDate":"2030-05-10T21:00:00", "capacity":50}
            """;

    private static final String VALID_UPDATE = """
            {"name":"Spring Conf", "description":"Workshops", "startDate":"2030-06-01T09:00:00",
             "endDate":"2030-06-01T17:00:00", "capacity":80}
            """;

    private static final String MISSING_EVENT = "/api/events/9223372036854775807";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    private String createEvent(String name) throws Exception {
        return mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("Java Conf", name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
    }

    private void changeStatus(String location, String newStatus, int expectedStatus) throws Exception {
        mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + newStatus + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private long idOf(String location) {
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void createsAndRetrievesAScheduledEvent() throws Exception {
        String location = mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Java Conf"))
                .andExpect(jsonPath("$.description").value("Talks"))
                .andExpect(jsonPath("$.startDate").value("2030-05-10T18:00:00"))
                .andExpect(jsonPath("$.endDate").value("2030-05-10T21:00:00"))
                .andExpect(jsonPath("$.capacity").value(50))
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getHeader("Location");

        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Java Conf"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void listsEventsInCreationOrder() throws Exception {
        createEvent("First");
        createEvent("Second");
        mockMvc.perform(get("/api/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("First"))
                .andExpect(jsonPath("$[1].name").value("Second"));
    }

    @Test
    void returnsNotFoundForMissingEvents() throws Exception {
        mockMvc.perform(get(MISSING_EVENT)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Event with id 9223372036854775807 was not found"))
                .andExpect(jsonPath("$.path").value(MISSING_EVENT));
        mockMvc.perform(put(MISSING_EVENT).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound());
        changeStatus(MISSING_EVENT, "ACTIVE", 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\" \",\"startDate\":\"2030-05-10T18:00:00\",\"endDate\":\"2030-05-10T21:00:00\",\"capacity\":5}",
            "{\"name\":\"A\",\"endDate\":\"2030-05-10T21:00:00\",\"capacity\":5}",
            "{\"name\":\"A\",\"startDate\":\"2030-05-10T18:00:00\",\"capacity\":5}",
            "{\"name\":\"A\",\"startDate\":\"2030-05-10T18:00:00\",\"endDate\":\"2030-05-10T21:00:00\"}",
            "{\"name\":\"A\",\"startDate\":\"2030-05-10T18:00:00\",\"endDate\":\"2030-05-10T21:00:00\",\"capacity\":0}",
            "{\"name\":\"A\",\"startDate\":\"2030-05-10T18:00:00\",\"endDate\":\"2030-05-10T21:00:00\",\"capacity\":-3}"
    })
    void rejectsInvalidCreationData(String body) throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details").isNotEmpty());
        mockMvc.perform(get("/api/events")).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-05-10T18:00:00", "2030-05-10T17:00:00"})
    void rejectsEndDateNotAfterStartDate(String endDate) throws Exception {
        String body = VALID_EVENT.replace("2030-05-10T21:00:00", endDate);
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End date must be after start date"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("2030-05-10T18:00:00", "not-a-date")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacesAllEditableFields() throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring Conf"))
                .andExpect(jsonPath("$.capacity").value(80));
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring Conf"))
                .andExpect(jsonPath("$.description").value("Workshops"))
                .andExpect(jsonPath("$.startDate").value("2030-06-01T09:00:00"))
                .andExpect(jsonPath("$.endDate").value("2030-06-01T17:00:00"))
                .andExpect(jsonPath("$.capacity").value(80))
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"Spring Conf\"}",
            "{\"name\":\"A\",\"startDate\":\"2030-06-01T09:00:00\",\"endDate\":\"2030-06-01T08:00:00\",\"capacity\":5}",
            "{\"name\":\"A\",\"startDate\":\"2030-06-01T09:00:00\",\"endDate\":\"2030-06-01T17:00:00\",\"capacity\":0}"
    })
    void rejectsInvalidUpdatesWithoutChangingEvent(String body) throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Java Conf"))
                .andExpect(jsonPath("$.capacity").value(50));
    }

    @Test
    void rejectsCapacityBelowRegisteredCount() throws Exception {
        String location = createEvent("Java Conf");
        eventRepository.findById(idOf(location)).orElseThrow().setRegisteredCount(90);
        entityManager.flush();

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Capacity cannot be lower than the registered count (90)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FINISHED", "CANCELLED"})
    void rejectsUpdatingClosedEvents(String closedStatus) throws Exception {
        String location = createEvent("Java Conf");
        changeStatus(location, "ACTIVE", 200);
        changeStatus(location, closedStatus, 200);

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event with status " + closedStatus + " cannot be modified"));
        mockMvc.perform(get(location)).andExpect(jsonPath("$.name").value("Java Conf"));
    }

    @Test
    void followsTheEventLifecycle() throws Exception {
        String location = createEvent("Java Conf");
        changeStatus(location, "ACTIVE", 200);
        changeStatus(location, "FINISHED", 200);
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get(location)).andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void cancelsAScheduledEvent() throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectsInvalidStatusTransitions() throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot change event status from SCHEDULED to FINISHED"));
        changeStatus(location, "CANCELLED", 200);
        changeStatus(location, "ACTIVE", 409);
        mockMvc.perform(get(location)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private void register(String eventLocation, String document, RegistrationStatus status) {
        User user = userRepository.save(new User("User " + document, document + "@example.com", document));
        Event event = eventRepository.findById(idOf(eventLocation)).orElseThrow();
        registrationRepository.save(new Registration(user, event, LocalDateTime.now(), status));
    }

    @Test
    void increasingTheCapacityPromotesTheWaitlistInOrder() throws Exception {
        String location = createEvent("Java Conf");
        eventRepository.findById(idOf(location)).orElseThrow().setRegisteredCount(50);
        register(location, "W1", RegistrationStatus.WAITING_LIST);
        register(location, "W2", RegistrationStatus.WAITING_LIST);
        register(location, "W3", RegistrationStatus.WAITING_LIST);
        entityManager.flush();

        mockMvc.perform(put(location).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("\"capacity\":50", "\"capacity\":52")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(52))
                .andExpect(jsonPath("$.registeredCount").value(52));

        mockMvc.perform(get(location + "/waitlist")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].userEmail").value("W3@example.com"));
        mockMvc.perform(get(location + "/registrations").param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userEmail").value("W1@example.com"))
                .andExpect(jsonPath("$[1].userEmail").value("W2@example.com"));
    }

    @Test
    void reportsAvailabilityOfANewEvent() throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(get(location + "/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(idOf(location)))
                .andExpect(jsonPath("$.capacity").value(50))
                .andExpect(jsonPath("$.registeredCount").value(0))
                .andExpect(jsonPath("$.availableSeats").value(50))
                .andExpect(jsonPath("$.waitlistCount").value(0))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.acceptingRegistrations").value(true));
    }

    @Test
    void reportsAFullEventCountingOnlyItsWaitlistedRegistrations() throws Exception {
        String location = createEvent("Full Conf");
        String otherLocation = createEvent("Other Conf");
        eventRepository.findById(idOf(location)).orElseThrow().setRegisteredCount(50);
        register(location, "W1", RegistrationStatus.WAITING_LIST);
        register(location, "W2", RegistrationStatus.WAITING_LIST);
        register(location, "C1", RegistrationStatus.CONFIRMED);
        register(location, "X1", RegistrationStatus.CANCELLED);
        register(otherLocation, "W3", RegistrationStatus.WAITING_LIST);
        entityManager.flush();

        mockMvc.perform(get(location + "/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(50))
                .andExpect(jsonPath("$.availableSeats").value(0))
                .andExpect(jsonPath("$.waitlistCount").value(2))
                .andExpect(jsonPath("$.acceptingRegistrations").value(true));
    }

    @Test
    void cancelledEventDoesNotAcceptRegistrations() throws Exception {
        String location = createEvent("Java Conf");
        changeStatus(location, "CANCELLED", 200);
        mockMvc.perform(get(location + "/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.availableSeats").value(50))
                .andExpect(jsonPath("$.acceptingRegistrations").value(false));
    }

    @Test
    void startedEventDoesNotAcceptRegistrations() throws Exception {
        String location = mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT.replace("2030-05-10T18:00:00", "2020-01-01T09:00:00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(get(location + "/availability")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.acceptingRegistrations").value(false));
    }

    @Test
    void returnsNotFoundForAvailabilityOfMissingEvent() throws Exception {
        mockMvc.perform(get(MISSING_EVENT + "/availability")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event with id 9223372036854775807 was not found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}"})
    void rejectsInvalidStatuses(String body) throws Exception {
        String location = createEvent("Java Conf");
        mockMvc.perform(patch(location + "/status").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(location)).andExpect(jsonPath("$.status").value("SCHEDULED"));
    }
}
