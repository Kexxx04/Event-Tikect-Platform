package steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class EventRegistrationSteps {

    private static final String DEFAULT_START = "2030-05-10T18:00:00";

    // Valid transitions needed to move a new SCHEDULED event to each closed status.
    private static final Map<String, List<String>> PATH_TO_STATUS = Map.of(
            "CANCELLED", List.of("CANCELLED"),
            "FINISHED", List.of("ACTIVE", "FINISHED")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionStatus transaction;
    private ResultActions response;
    private long eventId;
    private final Map<String, Long> userIds = new HashMap<>();
    private final List<String> confirmedParticipants = new ArrayList<>();

    @Before("@registrations")
    public void beginScenario() {
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    @After("@registrations")
    public void rollbackScenario() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    @Given("an open event with capacity {int}")
    public void anOpenEventWithCapacity(int capacity) throws Exception {
        createEvent(capacity, DEFAULT_START);
    }

    @Given("an event with capacity {int} that started at {string}")
    public void anEventThatStartedAt(int capacity, String startDate) throws Exception {
        createEvent(capacity, startDate);
    }

    @Given("the event has been {word}")
    public void theEventHasBeen(String closedStatus) throws Exception {
        for (String step : PATH_TO_STATUS.get(closedStatus)) {
            mockMvc.perform(patch("/api/events/" + eventId + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"" + step + "\"}"))
                    .andExpect(status().isOk());
        }
    }

    @Given("the event already has {int} confirmed registration(s)")
    public void theEventAlreadyHasConfirmedRegistrations(int count) throws Exception {
        for (int index = 1; index <= count; index++) {
            String email = "participant" + index + "@example.com";
            anActiveUser(email);
            sendRegistration(email).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
            confirmedParticipants.add(email);
        }
    }

    @Given("an active user {string}")
    public void anActiveUser(String email) throws Exception {
        Map<String, String> user = Map.of("name", "User " + email, "email", email, "document", "DOC-" + email);
        String location = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        userIds.put(email, idOf(location));
    }

    @Given("an inactive user {string}")
    public void anInactiveUser(String email) throws Exception {
        anActiveUser(email);
        mockMvc.perform(patch("/api/users/" + userIds.get(email) + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Given("{string} is registered for the event")
    public void isRegisteredForTheEvent(String email) throws Exception {
        sendRegistration(email).andExpect(status().isCreated());
    }

    @Given("the following active users register for the event:")
    public void theFollowingActiveUsersRegister(DataTable emails) throws Exception {
        for (String email : emails.asList()) {
            anActiveUser(email);
            isRegisteredForTheEvent(email);
        }
    }

    @When("{string} registers for the event")
    public void registersForTheEvent(String email) throws Exception {
        response = sendRegistration(email);
    }

    @When("{string} cancels their registration")
    public void cancelsTheirRegistration(String email) throws Exception {
        response = mockMvc.perform(delete("/api/events/" + eventId + "/registrations/" + userIds.get(email)));
    }

    @When("a confirmed participant cancels their registration")
    public void aConfirmedParticipantCancels() throws Exception {
        cancelsTheirRegistration(confirmedParticipants.remove(0));
    }

    @Then("the registration response has HTTP status code {int}")
    public void theRegistrationResponseHasStatus(int code) throws Exception {
        response.andExpect(status().is(code));
    }

    @Then("the registration status is {word}")
    public void theRegistrationStatusIs(String expectedStatus) throws Exception {
        response.andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Then("the registration error message is {string}")
    public void theRegistrationErrorMessageIs(String message) throws Exception {
        response.andExpect(jsonPath("$.message").value(message));
    }

    @Then("the cancellation promoted {string}")
    public void theCancellationPromoted(String email) throws Exception {
        response.andExpect(jsonPath("$.promoted.userEmail").value(email))
                .andExpect(jsonPath("$.promoted.status").value("CONFIRMED"));
    }

    @Then("nobody was promoted")
    public void nobodyWasPromoted() throws Exception {
        response.andExpect(jsonPath("$.cancelled.status").value("CANCELLED"))
                .andExpect(jsonPath("$.promoted").doesNotExist());
    }

    @Then("{string} has a {word} registration for the event")
    public void hasARegistrationForTheEvent(String email, String expectedStatus) throws Exception {
        mockMvc.perform(get("/api/events/" + eventId + "/registrations/" + userIds.get(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Then("the event has {int} registered participant(s) and {int} user(s) on the waitlist")
    public void theEventHasRegisteredParticipants(int registeredCount, int waitlistCount) throws Exception {
        mockMvc.perform(get("/api/events/" + eventId + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredCount").value(registeredCount))
                .andExpect(jsonPath("$.waitlistCount").value(waitlistCount));
    }

    @Then("the waitlist is, in order:")
    public void theWaitlistIsInOrder(DataTable emails) throws Exception {
        JsonNode waitlist = readJson(mockMvc.perform(get("/api/events/" + eventId + "/waitlist"))
                .andExpect(status().isOk()));
        List<String> actual = new ArrayList<>();
        for (int index = 0; index < waitlist.size(); index++) {
            assertEquals(index + 1, waitlist.get(index).get("position").asInt());
            actual.add(waitlist.get(index).get("userEmail").asString());
        }
        assertEquals(emails.asList(), actual);
    }

    @Then("the events of {string} include the event with registration status {word}")
    public void theEventsOfTheUserInclude(String email, String expectedStatus) throws Exception {
        JsonNode events = readJson(mockMvc.perform(get("/api/users/" + userIds.get(email) + "/events"))
                .andExpect(status().isOk()));
        Map<Long, String> statusByEvent = new LinkedHashMap<>();
        events.forEach(event -> statusByEvent.put(event.get("eventId").asLong(),
                event.get("registrationStatus").asString()));
        assertEquals(Map.of(eventId, expectedStatus), statusByEvent);
    }

    private void createEvent(int capacity, String startDate) throws Exception {
        Map<String, Object> event = Map.of(
                "name", "Java Conf",
                "startDate", startDate,
                "endDate", LocalDateTime.parse(startDate).plusHours(3).toString(),
                "capacity", capacity);
        String location = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        eventId = idOf(location);
    }

    private ResultActions sendRegistration(String email) throws Exception {
        return mockMvc.perform(post("/api/events/" + eventId + "/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userIds.get(email) + "}"));
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long idOf(String location) {
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }
}
