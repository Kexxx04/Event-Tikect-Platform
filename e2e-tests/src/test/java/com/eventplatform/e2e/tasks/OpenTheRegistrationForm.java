package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isEnabled;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import com.eventplatform.e2e.userinterfaces.RegistrationsSection;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Clicks "Register user" on the event page and waits for the list of users to register. */
public final class OpenTheRegistrationForm {

    private OpenTheRegistrationForm() {
    }

    public static Performable ofTheEvent() {
        return Task.where("{0} opens the registration form of the event",
                WaitUntil.the(EventDetailPage.REGISTER_USER, isEnabled()),
                Click.on(EventDetailPage.REGISTER_USER),
                WaitUntil.the(RegistrationsSection.USER_TO_REGISTER, isVisible()));
    }
}
