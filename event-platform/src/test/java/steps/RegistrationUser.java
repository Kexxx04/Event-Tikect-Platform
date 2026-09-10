package steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventplatform.api.repository.UserRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.After;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import tools.jackson.databind.ObjectMapper;

public class RegistrationUser {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionStatus transaction;
    private ResultActions response;
    private String existingUserLocation;
    private Map<String, String> existingUserData;
    private long initialUserCount;

    @Before
    public void beginScenario() {
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    @After
    public void rollbackScenario() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    @Given("no user exists with the email {string}")
    public void noUserExistsWithEmail(String email) {
        assertFalse(userRepository.existsByEmailIgnoreCase(email));
    }

    @Given("no user exists with the document {string}")
    public void noUserExistsWithDocument(String document) {
        assertFalse(userRepository.existsByDocument(document));
    }

    @Given("a registered user exists with the following data:")
    public void aRegisteredUserExists(DataTable table) throws Exception {
        existingUserData = table.asMap(String.class, String.class);
        existingUserLocation = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(existingUserData)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(existingUserLocation);
    }

    @When("I send a POST request to {string} with the following JSON:")
    public void sendPostRequest(String path, String json) throws Exception {
        initialUserCount = userRepository.count();
        response = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Then("the response has HTTP status code {int}")
    public void responseHasStatus(int code) throws Exception {
        response.andExpect(status().is(code));
    }

    @Then("it contains a generated user ID")
    public void responseContainsId() throws Exception {
        response.andExpect(jsonPath("$.id").isNumber());
        assertTrue(objectMapper.readTree(response.andReturn().getResponse()
                .getContentAsString()).get("id").asLong() > 0);
    }

    @Then("it contains the following user data:")
    public void responseContainsUserData(DataTable table) throws Exception {
        for (Map.Entry<String, String> entry : table.asMap(String.class, String.class).entrySet()) {
            response.andExpect(jsonPath("$." + entry.getKey()).value(entry.getValue()));
        }
    }

    @Then("the user is registered and can be retrieved by their ID")
    public void userCanBeRetrieved() throws Exception {
        var created = objectMapper.readTree(response.andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();
        assertTrue(userRepository.existsById(id));
        assertEquals(initialUserCount + 1, userRepository.count());
        assertEquals("/api/users/" + id, response.andReturn().getResponse().getHeader("Location"));
        var retrieved = mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(created, objectMapper.readTree(retrieved.getContentAsString()));
    }

    @Then("it contains the message {string}")
    public void responseContainsMessage(String message) throws Exception {
        response.andExpect(jsonPath("$.message").value(message));
    }

    @Then("no user is registered with the document {string}")
    public void noNewUserIsRegistered(String document) {
        assertFalse(userRepository.existsByDocument(document));
        assertEquals(initialUserCount, userRepository.count());
    }

    @Then("the existing user's data remains unchanged")
    public void existingUserRemainsUnchanged() throws Exception {
        ResultActions existing = mockMvc.perform(get(existingUserLocation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        for (Map.Entry<String, String> entry : existingUserData.entrySet()) {
            existing.andExpect(jsonPath("$." + entry.getKey()).value(entry.getValue()));
        }
    }
}
