package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.interactions.EnterDateTime;
import com.eventplatform.e2e.models.EventData;
import com.eventplatform.e2e.userinterfaces.AppShell;
import com.eventplatform.e2e.userinterfaces.EventFormPage;
import com.eventplatform.e2e.userinterfaces.EventListPage;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Goes to Events > New event, fills in every field and submits. Used for valid and invalid data. */
public class FillInTheEventForm implements Task {

    private final EventData event;
    private final String eventName;

    public FillInTheEventForm(EventData event) {
        this.event = event;
        this.eventName = event.name();
    }

    public static FillInTheEventForm with(EventData event) {
        return instrumented(FillInTheEventForm.class, event);
    }

    @Override
    @Step("{0} submits the new event form for '#eventName'")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                Click.on(AppShell.EVENTS_MENU),
                Click.on(EventListPage.NEW_EVENT),
                WaitUntil.the(EventFormPage.NAME, isVisible()),
                Enter.theValue(event.name()).into(EventFormPage.NAME),
                Enter.theValue(event.description()).into(EventFormPage.DESCRIPTION),
                EnterDateTime.of(event.startDate()).into(EventFormPage.START_DATE),
                EnterDateTime.of(event.endDate()).into(EventFormPage.END_DATE),
                Enter.theValue(event.capacity()).into(EventFormPage.CAPACITY),
                Click.on(EventFormPage.SAVE));
    }
}
