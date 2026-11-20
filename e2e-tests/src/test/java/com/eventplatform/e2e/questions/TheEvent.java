package com.eventplatform.e2e.questions;

import com.eventplatform.e2e.models.CreatedEvent;
import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import com.eventplatform.e2e.userinterfaces.EventListPage;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;

/** Status and availability of an event, as the frontend shows them. */
public final class TheEvent {

    private TheEvent() {
    }

    /** Status badge of the event page, e.g. "Scheduled". */
    public static Question<String> status() {
        return Text.of(EventDetailPage.STATUS).describedAs("the status of the event");
    }

    /** Status badge of the event card in the events list. */
    public static Question<String> statusInTheList(CreatedEvent event) {
        return Text.of(EventListPage.CARD_STATUS.of(event.name()))
                .describedAs("the status of " + event.name() + " in the events list");
    }

    /** "Seats available" tile: capacity minus confirmed registrations. */
    public static Question<Integer> availableSeats() {
        return Text.of(EventDetailPage.AVAILABLE_SEATS).asInteger().describedAs("the available seats");
    }

    /** "Waitlist" tile: registrations waiting for a seat. */
    public static Question<Integer> peopleOnTheWaitlist() {
        return Text.of(EventDetailPage.WAITLIST_COUNT).asInteger().describedAs("the people on the waitlist");
    }
}
