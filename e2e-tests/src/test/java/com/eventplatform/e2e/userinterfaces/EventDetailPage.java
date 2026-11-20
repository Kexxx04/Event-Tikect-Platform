package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /events/{id}: header, status actions and availability tiles (EventDetailPage.tsx, AvailabilityTiles.tsx). */
public final class EventDetailPage {

    public static final Target TITLE = Target.the("event title").locatedBy("css:[data-testid='event-title']");
    public static final Target STATUS = Target.the("event status").locatedBy("css:[data-testid='event-status']");
    public static final Target EDIT = Target.the("Edit event button").locatedBy("css:a[href$='/edit']");
    /** Button that moves the event to the status {0} (active, finished or cancelled). */
    public static final Target STATUS_ACTION = Target.the("{0} status button").locatedBy("css:[data-testid='status-action-{0}']");
    public static final Target CONFIRM = Target.the("confirm button").locatedBy("css:[data-testid='confirm-button']");
    public static final Target REGISTER_USER = Target.the("Register user button").locatedBy("css:[data-testid='open-register-panel']");

    public static final Target AVAILABLE_SEATS = Target.the("available seats").locatedBy("css:[data-testid='availability-seats']");
    public static final Target WAITLIST_COUNT = Target.the("people on the waitlist").locatedBy("css:[data-testid='availability-waitlist']");

    private EventDetailPage() {
    }
}
