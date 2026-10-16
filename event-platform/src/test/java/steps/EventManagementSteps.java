package steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.api.repository.EventRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
import tools.jackson.databind.ObjectMapper;

public class EventManagementSteps {

    // Valid transitions needed to move a new SCHEDULED event to each status.
    private static final Map<String, List<String>> PATH_TO_STATUS = Map.of(
            "SCHEDULED", List.of(),
            "ACTIVE", List.of("ACTIVE"),
            "FINISHED", List.of("ACTIVE", "FINISHED"),
            "CANCELLED", List.of("CANCELLED")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EventRepository eventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionStatus transaction;
    private ResultActions response;
    private Map<String, Object> eventData;
    private String eventLocation;
    private long initialEventCount;

    @Before
    public void beginScenario() {
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        initialEventCount = eventRepository.count();
    }

    @After
    public void rollbackScenario() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    @Given("the following event data:")
    public void theFollowingEventData(DataTable table) {
        eventData = new LinkedHashMap<>(table.asMap(String.class, String.class));
        eventData.put("capacity", Integer.valueOf((String) eventData.get("capacity")));
    }

    @Given("the event capacity is {int}")
    public void theEventCapacityIs(int capacity) {
        eventData.put("capacity", capacity);
    }

    @Given("the event starts at {string}")
    public void theEventStartsAt(String startDate) {
        eventData.put("startDate", startDate);
    }

    @Given("an existing event with status {word}")
    public void anExistingEventWithStatus(String targetStatus) throws Exception {
        eventLocation = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventData)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(eventLocation);
        for (String step : PATH_TO_STATUS.get(targetStatus)) {
            sendStatusChange(step).andExpect(status().isOk());
        }
    }

    @When("the administrator creates the event")
    public void theAdministratorCreatesTheEvent() throws Exception {
        response = mockMvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventData)));
        eventLocation = response.andReturn().getResponse().getHeader("Location");
    }

    @When("the administrator changes the event status to {word}")
    public void theAdministratorChangesTheEventStatus(String newStatus) throws Exception {
        response = sendStatusChange(newStatus);
    }

    @When("the administrator replaces the event name with {string}")
    public void theAdministratorReplacesTheEventName(String name) throws Exception {
        eventData.put("name", name);
        response = mockMvc.perform(put(eventLocation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(eventData)));
    }

    @When("the administrator checks the event availability")
    public void theAdministratorChecksTheEventAvailability() throws Exception {
        response = mockMvc.perform(get(eventLocation + "/availability"));
    }

    @Then("the event has {int} available seats")
    public void theEventHasAvailableSeats(int availableSeats) throws Exception {
        response.andExpect(jsonPath("$.availableSeats").value(availableSeats));
    }

    @Then("the event is accepting registrations")
    public void theEventIsAcceptingRegistrations() throws Exception {
        response.andExpect(jsonPath("$.acceptingRegistrations").value(true));
    }

    @Then("the event is not accepting registrations")
    public void theEventIsNotAcceptingRegistrations() throws Exception {
        response.andExpect(jsonPath("$.acceptingRegistrations").value(false));
    }

    @Then("the event response has HTTP status code {int}")
    public void theEventResponseHasStatus(int code) throws Exception {
        response.andExpect(status().is(code));
    }

    @Then("the event is SCHEDULED with {int} registered participants and capacity {int}")
    public void theEventIsScheduled(int registeredCount, int capacity) throws Exception {
        response.andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.registeredCount").value(registeredCount))
                .andExpect(jsonPath("$.capacity").value(capacity));
        mockMvc.perform(get(eventLocation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Then("the validation error for {string} is {string}")
    public void theValidationErrorIs(String field, String message) throws Exception {
        response.andExpect(jsonPath("$.details." + field).value(message));
    }

    @Then("no event has been created")
    public void noEventHasBeenCreated() {
        assertEquals(initialEventCount, eventRepository.count());
    }

    @Then("the event error message is {string}")
    public void theEventErrorMessageIs(String message) throws Exception {
        response.andExpect(jsonPath("$.message").value(message));
    }

    @Then("the event status is {word}")
    public void theEventStatusIs(String expectedStatus) throws Exception {
        mockMvc.perform(get(eventLocation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private ResultActions sendStatusChange(String newStatus) throws Exception {
        return mockMvc.perform(patch(eventLocation + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + newStatus + "\"}"));
    }
}
