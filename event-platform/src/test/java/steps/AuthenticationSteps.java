package steps;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

public class AuthenticationSteps {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Value("${app.security.admin.username}") private String adminUsername;
    @Value("${app.security.admin.password}") private String adminPassword;

    private ResultActions response;
    private String token;

    @When("the administrator logs in with valid credentials")
    public void theAdministratorLogsIn() throws Exception {
        logIn(adminUsername, adminPassword);
        token = objectMapper.readTree(response.andReturn().getResponse().getContentAsString())
                .path("token").asString();
    }

    @When("someone logs in with username {string} and password {string}")
    public void someoneLogsIn(String username, String password) throws Exception {
        logIn(username, password);
    }

    @When("the client requests {string} with the issued token")
    public void requestWithIssuedToken(String path) throws Exception {
        response = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @When("the client requests {string} without a token")
    public void requestWithoutToken(String path) throws Exception {
        response = mockMvc.perform(get(path));
    }

    @When("the client requests {string} with an invalid token")
    public void requestWithInvalidToken(String path) throws Exception {
        response = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"));
    }

    @Then("the authentication response has HTTP status code {int}")
    public void theAuthenticationResponseHasStatus(int code) throws Exception {
        response.andExpect(status().is(code));
    }

    @Then("the response contains a bearer token for {string}")
    public void theResponseContainsABearerToken(String username) throws Exception {
        response.andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value(username));
        assertFalse(token.isBlank());
    }

    @Then("the authentication error message is {string}")
    public void theAuthenticationErrorMessageIs(String message) throws Exception {
        response.andExpect(jsonPath("$.message").value(message));
    }

    @Then("no token is returned")
    public void noTokenIsReturned() throws Exception {
        response.andExpect(jsonPath("$.token").doesNotExist());
    }

    private void logIn(String username, String password) throws Exception {
        response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }
}
