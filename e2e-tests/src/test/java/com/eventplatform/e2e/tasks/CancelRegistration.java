package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.containsText;

import com.eventplatform.e2e.models.RegistrationStatus;
import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.userinterfaces.EventDetailPage;
import com.eventplatform.e2e.userinterfaces.RegistrationsSection;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.conditions.Check;
import net.serenitybdd.screenplay.questions.Visibility;
import net.serenitybdd.screenplay.waits.WaitUntil;

/**
 * Cancels a registration from the table of the event page (DELETE /api/events/{id}/registrations/{userId}).
 * The registration is looked up among the confirmed ones and then on the waitlist.
 */
public class CancelRegistration implements Task {

    private final UserData user;
    private final String userName;

    public CancelRegistration(UserData user) {
        this.user = user;
        this.userName = user.name();
    }

    public static CancelRegistration of(UserData user) {
        return instrumented(CancelRegistration.class, user);
    }

    @Override
    @Step("{0} cancels the registration of '#userName'")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                ShowRegistrations.withStatus(RegistrationStatus.CONFIRMED),
                Check.whether(Visibility.of(RegistrationsSection.ROW.of(user.email())))
                        .otherwise(ShowRegistrations.withStatus(RegistrationStatus.WAITING_LIST)),
                Click.on(RegistrationsSection.CANCEL.of(user.email())),
                Click.on(EventDetailPage.CONFIRM),
                WaitUntil.the(RegistrationsSection.NOTICE, containsText("Registration of " + user.name() + " cancelled")));
    }
}
