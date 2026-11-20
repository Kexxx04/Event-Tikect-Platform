package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /events/new and /events/{id}/edit (frontend/src/pages/events/EventFormPage.tsx). */
public final class EventFormPage {

    public static final String NEW_EVENT_PATH = "/events/new";

    public static final Target NAME = Target.the("event name field").locatedBy("css:#name");
    public static final Target DESCRIPTION = Target.the("event description field").locatedBy("css:#description");
    public static final Target START_DATE = Target.the("start date field").locatedBy("css:#startDate");
    public static final Target END_DATE = Target.the("end date field").locatedBy("css:#endDate");
    public static final Target CAPACITY = Target.the("capacity field").locatedBy("css:#capacity");
    public static final Target SAVE = Target.the("save event button").locatedBy("css:form button[type='submit']");

    private EventFormPage() {
    }
}
