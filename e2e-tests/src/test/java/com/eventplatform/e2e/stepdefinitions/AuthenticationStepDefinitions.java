package com.eventplatform.e2e.stepdefinitions;

import static com.eventplatform.e2e.stepdefinitions.StageHooks.administrator;
import static com.eventplatform.e2e.utils.Eventually.eventually;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;
import static org.hamcrest.Matchers.equalTo;

import com.eventplatform.e2e.questions.TheCurrentPage;
import com.eventplatform.e2e.questions.TheSignedInUser;
import com.eventplatform.e2e.tasks.Login;
import com.eventplatform.e2e.tasks.NavigateTo;
import com.eventplatform.e2e.tasks.SignOut;
import com.eventplatform.e2e.userinterfaces.AppShell;
import com.eventplatform.e2e.userinterfaces.LoginPage;
import com.eventplatform.e2e.utils.TestConfiguration;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.questions.Visibility;
import net.serenitybdd.screenplay.waits.WaitUntil;

public class AuthenticationStepDefinitions {

    @Given("the administrator is on the login page")
    public void theAdministratorIsOnTheLoginPage() {
        administrator().attemptsTo(NavigateTo.theLoginPage());
    }

    @Given("the administrator has signed in")
    public void theAdministratorHasSignedIn() {
        administrator().attemptsTo(
                NavigateTo.theLoginPage(),
                Login.withConfiguredCredentials(),
                WaitUntil.the(AppShell.SIGNED_IN_USERNAME, isVisible()));
    }

    @When("the administrator signs in with valid credentials")
    public void theAdministratorSignsInWithValidCredentials() {
        administrator().attemptsTo(Login.withConfiguredCredentials());
    }

    @When("the administrator signs in with username {string} and password {string}")
    public void theAdministratorSignsInWith(String username, String password) {
        administrator().attemptsTo(Login.withCredentials(username, password));
    }

    @When("the administrator signs out")
    public void theAdministratorSignsOut() {
        administrator().attemptsTo(SignOut.fromTheSidebar());
    }

    @When("the administrator opens the {string} page without a session")
    public void theAdministratorOpensAPageWithoutASession(String path) {
        administrator().attemptsTo(NavigateTo.thePath(path));
    }

    @Then("the administrator should see the {string} page")
    public void theAdministratorShouldSeeThePage(String title) {
        administrator().should(
                eventually(seeThat(TheCurrentPage.path(), equalTo("/" + title.toLowerCase()))),
                eventually(seeThat(TheCurrentPage.title(), equalTo(title))));
    }

    @Then("the signed-in username should be shown in the sidebar")
    public void theSignedInUsernameShouldBeShown() {
        administrator().should(eventually(seeThat(TheSignedInUser.name(), equalTo(TestConfiguration.adminUsername()))));
    }

    @Then("the administrator should be on the login page")
    @Then("the administrator should stay on the login page")
    public void theAdministratorShouldBeOnTheLoginPage() {
        administrator().should(
                eventually(seeThat(TheCurrentPage.path(), equalTo(LoginPage.PATH))),
                seeThat(Visibility.of(LoginPage.SIGN_IN), equalTo(true)));
    }

    @Then("opening the {string} page should show the login page again")
    public void openingAProtectedPageShouldShowTheLoginPage(String path) {
        administrator().attemptsTo(NavigateTo.thePath(path));
        theAdministratorShouldBeOnTheLoginPage();
    }
}
