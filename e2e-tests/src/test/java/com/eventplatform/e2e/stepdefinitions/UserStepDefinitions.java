package com.eventplatform.e2e.stepdefinitions;

import static com.eventplatform.e2e.stepdefinitions.StageHooks.administrator;

import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.tasks.CreateUser;
import com.eventplatform.e2e.tasks.FillInTheUserForm;
import com.eventplatform.e2e.utils.ActorMemory;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class UserStepDefinitions {

    @Given("the administrator has created the users:")
    public void theAdministratorHasCreatedTheUsers(DataTable users) {
        users.asMaps().forEach(row -> administrator().attemptsTo(CreateUser.with(UserData.named(row.get("name")))));
    }

    @When("the administrator tries to create the user {string} with the email of {string}")
    public void theAdministratorTriesToCreateAUserWithARepeatedEmail(String name, String existingUser) {
        String repeatedEmail = ActorMemory.user(administrator(), existingUser).email();
        administrator().attemptsTo(FillInTheUserForm.with(UserData.named(name).withEmail(repeatedEmail)));
    }
}
