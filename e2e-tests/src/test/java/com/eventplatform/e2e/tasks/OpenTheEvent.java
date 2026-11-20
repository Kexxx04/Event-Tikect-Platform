package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.containsText;

import com.eventplatform.e2e.models.CreatedEvent;
import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Open;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Opens /events/{id} and waits until the page shows the event, so its data is loaded again. */
public final class OpenTheEvent {

    private OpenTheEvent() {
    }

    public static Performable page(CreatedEvent event) {
        return Task.where("{0} opens the page of the event " + event.name(),
                Open.relativeUrl(event.path()),
                WaitUntil.the(EventDetailPage.TITLE, containsText(event.name())));
    }
}
