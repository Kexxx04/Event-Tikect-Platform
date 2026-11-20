package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import com.eventplatform.e2e.userinterfaces.EventFormPage;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** From the event page: Edit, type the new capacity and save (PUT /api/events/{id}). */
public class ChangeEventCapacity implements Task {

    private final Integer capacity;

    public ChangeEventCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public static ChangeEventCapacity to(Integer capacity) {
        return instrumented(ChangeEventCapacity.class, capacity);
    }

    @Override
    @Step("{0} changes the capacity of the event to #capacity")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                Click.on(EventDetailPage.EDIT),
                WaitUntil.the(EventFormPage.CAPACITY, isVisible()),
                Enter.theValue(String.valueOf(capacity)).into(EventFormPage.CAPACITY),
                Click.on(EventFormPage.SAVE));
    }
}
