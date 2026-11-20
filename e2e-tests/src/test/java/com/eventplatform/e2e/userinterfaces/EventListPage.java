package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /events (frontend/src/pages/events/EventsPage.tsx). */
public final class EventListPage {

    public static final Target NEW_EVENT = Target.the("New event button").locatedBy("css:a[href='/events/new']");
    public static final Target SEARCH = Target.the("event search field").locatedBy("css:#event-search");
    /** Status badge of the event card whose title is {0}. */
    public static final Target CARD_STATUS = Target.the("status of the event card {0}")
            .locatedBy("//article[@data-testid='event-card'][.//h2/a[normalize-space()='{0}']]"
                    + "//*[@data-testid='event-card-status']");

    private EventListPage() {
    }
}
