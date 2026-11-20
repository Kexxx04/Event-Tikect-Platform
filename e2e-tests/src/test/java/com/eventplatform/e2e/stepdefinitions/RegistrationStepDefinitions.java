package com.eventplatform.e2e.stepdefinitions;

import static com.eventplatform.e2e.stepdefinitions.StageHooks.administrator;
import static com.eventplatform.e2e.utils.Eventually.eventually;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import com.eventplatform.e2e.models.RegistrationStatus;
import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.questions.TheRegistrations;
import com.eventplatform.e2e.tasks.CancelRegistration;
import com.eventplatform.e2e.tasks.OpenTheEvent;
import com.eventplatform.e2e.tasks.OpenTheRegistrationForm;
import com.eventplatform.e2e.tasks.RegisterUser;
import com.eventplatform.e2e.tasks.ShowRegistrations;
import com.eventplatform.e2e.utils.ActorMemory;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.screenplay.Actor;

public class RegistrationStepDefinitions {

    @Given("the administrator has registered these users to the event:")
    public void theAdministratorHasRegisteredTheseUsers(DataTable users) {
        theAdministratorRegistersTheseUsers(users);
    }

    @When("the administrator registers these users to the event:")
    public void theAdministratorRegistersTheseUsers(DataTable users) {
        Actor administrator = administrator();
        administrator.attemptsTo(OpenTheEvent.page(ActorMemory.currentEvent(administrator)));
        users.asMaps().forEach(row ->
                administrator.attemptsTo(RegisterUser.toTheEvent(ActorMemory.user(administrator, row.get("user")))));
    }

    @When("the administrator cancels the registration of {string}")
    public void theAdministratorCancelsTheRegistrationOf(String name) {
        administrator().attemptsTo(CancelRegistration.of(ActorMemory.user(administrator(), name)));
    }

    @When("the administrator opens the registration form of the event")
    public void theAdministratorOpensTheRegistrationForm() {
        administrator().attemptsTo(
                OpenTheEvent.page(ActorMemory.currentEvent(administrator())),
                OpenTheRegistrationForm.ofTheEvent());
    }

    @Then("the registrations of the event should be:")
    public void theRegistrationsOfTheEventShouldBe(DataTable expectedRegistrations) {
        Actor administrator = administrator();
        expectedRegistrations.asMaps().forEach(row -> {
            UserData user = ActorMemory.user(administrator, row.get("user"));
            RegistrationStatus status = RegistrationStatus.valueOf(row.get("status"));
            administrator.attemptsTo(ShowRegistrations.withStatus(status));
            administrator.should(eventually(seeThat(TheRegistrations.statusOf(user), equalTo(status.label()))));
        });
    }

    @Then("{string} should be number {int} on the waitlist")
    public void theUserShouldBeOnTheWaitlistAt(String name, int position) {
        UserData user = ActorMemory.user(administrator(), name);
        administrator().attemptsTo(ShowRegistrations.withStatus(RegistrationStatus.WAITING_LIST));
        administrator().should(eventually(seeThat(TheRegistrations.waitlistPositionOf(user), equalTo("#" + position + " in line"))));
    }

    @Then("{string} should be promoted from the waitlist")
    public void theUserShouldBePromotedFromTheWaitlist(String name) {
        administrator().should(eventually(seeThat(
                TheRegistrations.notice(), containsString(name + " was promoted from the waitlist"))));
    }

    @Then("{string} should not be offered for registration")
    public void theUserShouldNotBeOffered(String name) {
        UserData user = ActorMemory.user(administrator(), name);
        administrator().should(seeThat(TheRegistrations.usersOfferedForRegistration(), not(hasItem(user.registrationOption()))));
    }

    @Then("{string} should be offered for registration")
    public void theUserShouldBeOffered(String name) {
        UserData user = ActorMemory.user(administrator(), name);
        administrator().should(seeThat(TheRegistrations.usersOfferedForRegistration(), hasItem(user.registrationOption())));
    }
}
