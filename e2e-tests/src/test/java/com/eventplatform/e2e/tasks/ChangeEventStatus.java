package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;

import com.eventplatform.e2e.models.EventStatus;
import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.conditions.Check;

/** Clicks the status button of the event page; finishing and cancelling also need a confirmation. */
public class ChangeEventStatus implements Task {

    private final EventStatus status;

    public ChangeEventStatus(EventStatus status) {
        this.status = status;
    }

    public static ChangeEventStatus to(EventStatus status) {
        return instrumented(ChangeEventStatus.class, status);
    }

    @Override
    @Step("{0} changes the status of the event to #status")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                Click.on(EventDetailPage.STATUS_ACTION.of(status.actionId())),
                Check.whether(status.confirmationRequired()).andIfSo(Click.on(EventDetailPage.CONFIRM)));
    }
}
