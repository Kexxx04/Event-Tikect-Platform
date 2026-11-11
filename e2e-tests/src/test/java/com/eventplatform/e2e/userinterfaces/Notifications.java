package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** Messages shared by every page: alerts (Alert.tsx) and inline field errors (FormField.tsx). */
public final class Notifications {

    /** Error alert of the page (data-testid="alert-error"), e.g. a 401 or 409 message from the API. */
    public static final Target ERROR = Target.the("error message").locatedBy("css:[data-testid='alert-error']");
    /** Validation message that FormField renders under the input {0} with the id "{0}-error". */
    public static final Target FIELD_ERROR = Target.the("error of the {0} field").locatedBy("css:#{0}-error");

    private Notifications() {
    }
}
