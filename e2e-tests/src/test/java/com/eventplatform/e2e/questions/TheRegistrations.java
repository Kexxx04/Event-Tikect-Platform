package com.eventplatform.e2e.questions;

import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.userinterfaces.RegistrationsSection;
import java.util.List;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.SelectOptions;
import net.serenitybdd.screenplay.questions.Text;

/** Registrations card of the event page. */
public final class TheRegistrations {

    private TheRegistrations() {
    }

    /** Status badge of the user's row in the table currently shown, e.g. "Waitlisted". */
    public static Question<String> statusOf(UserData user) {
        return Text.of(RegistrationsSection.STATUS.of(user.email()))
                .describedAs("the registration status of " + user.name());
    }

    /** Position shown next to a waitlisted registration, e.g. "#1 in line". */
    public static Question<String> waitlistPositionOf(UserData user) {
        return Text.of(RegistrationsSection.WAITLIST_POSITION.of(user.email()))
                .describedAs("the waitlist position of " + user.name());
    }

    /** Last notice of the card, e.g. "... Grace Hopper was promoted from the waitlist". */
    public static Question<String> notice() {
        return Text.of(RegistrationsSection.NOTICE).describedAs("the registrations notice");
    }

    /** Options of the "User to register" list (users without an active registration). */
    public static Question<List<String>> usersOfferedForRegistration() {
        return SelectOptions.of(RegistrationsSection.USER_TO_REGISTER).describedAs("the users offered for registration");
    }
}
