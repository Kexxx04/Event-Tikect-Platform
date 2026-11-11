package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** "Registrations" card of the event page (frontend/src/pages/events/RegistrationsCard.tsx). */
public final class RegistrationsSection {

    private static final String SECTION = "css:section[aria-labelledby='registrations-title']";
    /** Row of the registrations table whose email cell is {0}. */
    private static final String ROW_OF = "//tr[@data-testid='registration-row'][td[normalize-space()='{0}']]";

    public static final Target USER_TO_REGISTER = Target.the("user to register list").locatedBy("css:#register-user");
    public static final Target REGISTER = Target.the("Register button").locatedBy(SECTION + " form button[type='submit']");
    public static final Target NOTICE = Target.the("registrations notice").locatedBy(SECTION + " [data-testid='alert-success']");
    /** Filter pill for the status {0} (confirmed, waiting_list or cancelled). */
    public static final Target FILTER = Target.the("{0} registrations filter").locatedBy(SECTION + " [data-testid='filter-{0}']");

    public static final Target ROW = Target.the("registration of {0}").locatedBy(ROW_OF);
    public static final Target STATUS = Target.the("registration status of {0}")
            .locatedBy(ROW_OF + "//*[@data-testid='registration-status']");
    public static final Target WAITLIST_POSITION = Target.the("waitlist position of {0}")
            .locatedBy(ROW_OF + "//*[@data-testid='waitlist-position']");
    public static final Target CANCEL = Target.the("Cancel button of the registration of {0}")
            .locatedBy(ROW_OF + "//button[@data-testid='cancel-registration']");

    private RegistrationsSection() {
    }
}
