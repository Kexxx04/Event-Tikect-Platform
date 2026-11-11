package com.eventplatform.e2e.tasks;

import com.eventplatform.e2e.models.CreatedEvent;
import com.eventplatform.e2e.userinterfaces.AppShell;
import com.eventplatform.e2e.userinterfaces.EventListPage;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;

/** Opens the events list from the sidebar and filters it by the event name. */
public final class SearchForTheEvent {

    private SearchForTheEvent() {
    }

    public static Performable named(CreatedEvent event) {
        return Task.where("{0} searches the events list for " + event.name(),
                Click.on(AppShell.EVENTS_MENU),
                Enter.theValue(event.name()).into(EventListPage.SEARCH));
    }
}
