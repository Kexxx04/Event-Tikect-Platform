package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.containsText;

import com.eventplatform.e2e.models.CreatedEvent;
import com.eventplatform.e2e.models.EventData;
import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import com.eventplatform.e2e.utils.ActorMemory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.questions.page.TheWebPage;
import net.serenitybdd.screenplay.waits.WaitUntil;

/**
 * Creates an event through the form and waits for its page (/events/{id}). The actor remembers the
 * event and its id so the next steps can open it again.
 */
public class CreateEvent implements Task {

    private static final Pattern EVENT_ID = Pattern.compile("/events/(\\d+)");

    private final EventData event;
    private final String eventName;
    private final String capacity;

    public CreateEvent(EventData event) {
        this.event = event;
        this.eventName = event.name();
        this.capacity = event.capacity();
    }

    public static CreateEvent with(EventData event) {
        return instrumented(CreateEvent.class, event);
    }

    @Override
    @Step("{0} creates the event '#eventName' with capacity #capacity")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                FillInTheEventForm.with(event),
                WaitUntil.the(EventDetailPage.TITLE, containsText(event.name())));

        String url = TheWebPage.currentUrl().answeredBy(actor);
        Matcher matcher = EVENT_ID.matcher(url);
        if (!matcher.find()) {
            throw new IllegalStateException("The event page URL has no id: " + url);
        }
        ActorMemory.rememberEvent(actor, new CreatedEvent(Long.parseLong(matcher.group(1)), event));
    }
}
