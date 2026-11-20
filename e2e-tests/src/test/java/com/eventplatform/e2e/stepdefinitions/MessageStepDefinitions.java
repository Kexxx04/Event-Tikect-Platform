package com.eventplatform.e2e.stepdefinitions;

import static com.eventplatform.e2e.stepdefinitions.StageHooks.administrator;
import static com.eventplatform.e2e.utils.Eventually.eventually;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

import com.eventplatform.e2e.questions.TheMessages;
import io.cucumber.java.en.Then;

/** Messages shared by every feature: API errors and form validations. */
public class MessageStepDefinitions {

    @Then("the error {string} should be shown")
    public void theErrorShouldBeShown(String message) {
        administrator().should(eventually(seeThat(TheMessages.error(), equalTo(message))));
    }

    @Then("the {string} field should show the error {string}")
    public void theFieldShouldShowTheError(String field, String message) {
        administrator().should(eventually(seeThat(TheMessages.errorOfTheField(field), equalTo(message))));
    }
}
