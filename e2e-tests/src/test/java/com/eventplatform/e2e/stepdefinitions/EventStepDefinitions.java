package com.eventplatform.e2e.stepdefinitions;

import static com.eventplatform.e2e.stepdefinitions.StageHooks.administrator;
import static com.eventplatform.e2e.utils.Eventually.eventually;
import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.equalTo;

import com.eventplatform.e2e.models.EventData;
import com.eventplatform.e2e.models.EventStatus;
import com.eventplatform.e2e.questions.TheCurrentPage;
import com.eventplatform.e2e.questions.TheEvent;
import com.eventplatform.e2e.tasks.ChangeEventCapacity;
import com.eventplatform.e2e.tasks.ChangeEventStatus;
import com.eventplatform.e2e.tasks.CreateEvent;
import com.eventplatform.e2e.tasks.FillInTheEventForm;
import com.eventplatform.e2e.tasks.OpenTheEvent;
import com.eventplatform.e2e.tasks.SearchForTheEvent;
import com.eventplatform.e2e.userinterfaces.EventFormPage;
import com.eventplatform.e2e.utils.ActorMemory;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class EventStepDefinitions {

    @Given("the administrator has created the event {string} with capacity {int} starting in {int} days")
    public void theAdministratorHasCreatedTheEvent(String name, int capacity, int days) {
        administrator().attemptsTo(CreateEvent.with(EventData.called(name, capacity, days)));
    }

    @When("the administrator tries to create an event starting in {int} days and ending in {int} days with capacity {string}")
    public void theAdministratorTriesToCreateAnEvent(int startDays, int endDays, String capacity) {
        administrator().attemptsTo(FillInTheEventForm.with(EventData.between("Invalid Event", capacity, startDays, endDays)));
    }

    @When("the administrator looks for the event in the events list")
    public void theAdministratorLooksForTheEvent() {
        administrator().attemptsTo(SearchForTheEvent.named(ActorMemory.currentEvent(administrator())));
    }

    @When("the administrator changes the status of the event to {string}")
    public void theAdministratorChangesTheStatusOfTheEvent(String status) {
        administrator().attemptsTo(
                OpenTheEvent.page(ActorMemory.currentEvent(administrator())),
                ChangeEventStatus.to(EventStatus.valueOf(status)));
    }

    @When("the administrator changes the capacity of the event to {int}")
    public void theAdministratorChangesTheCapacityOfTheEvent(int capacity) {
        administrator().attemptsTo(
                OpenTheEvent.page(ActorMemory.currentEvent(administrator())),
                ChangeEventCapacity.to(capacity));
    }

    @Then("the event should be listed with status {string}")
    public void theEventShouldBeListedWithStatus(String status) {
        administrator().should(eventually(seeThat(
                TheEvent.statusInTheList(ActorMemory.currentEvent(administrator())),
                equalTo(EventStatus.valueOf(status).label()))));
    }

    @Then("the status of the event should be {string}")
    public void theStatusOfTheEventShouldBe(String status) {
        administrator().should(eventually(seeThat(TheEvent.status(), equalTo(EventStatus.valueOf(status).label()))));
    }

    @Then("the event should not be created")
    public void theEventShouldNotBeCreated() {
        administrator().should(seeThat(TheCurrentPage.path(), equalTo(EventFormPage.NEW_EVENT_PATH)));
    }

    @Then("the event should show {int} available seat(s) and {int} person/people on the waitlist")
    public void theEventShouldShowItsAvailability(int availableSeats, int waitlisted) {
        administrator().should(
                eventually(seeThat(TheEvent.availableSeats(), equalTo(availableSeats))),
                eventually(seeThat(TheEvent.peopleOnTheWaitlist(), equalTo(waitlisted))));
    }
}
