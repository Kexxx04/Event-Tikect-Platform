package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.containsText;

import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.userinterfaces.RegistrationsSection;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.SelectFromOptions;
import net.serenitybdd.screenplay.waits.WaitUntil;

/**
 * Registers a user from the event page. The frontend confirms with "{name} is confirmed" or
 * "The event is full: {name} was added to the waitlist"; the task waits for that notice.
 */
public class RegisterUser implements Task {

    private final UserData user;
    private final String userName;

    public RegisterUser(UserData user) {
        this.user = user;
        this.userName = user.name();
    }

    public static RegisterUser toTheEvent(UserData user) {
        return instrumented(RegisterUser.class, user);
    }

    @Override
    @Step("{0} registers '#userName' to the event")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                OpenTheRegistrationForm.ofTheEvent(),
                SelectFromOptions.byVisibleText(user.registrationOption()).from(RegistrationsSection.USER_TO_REGISTER),
                Click.on(RegistrationsSection.REGISTER),
                WaitUntil.the(RegistrationsSection.NOTICE, containsText(user.name())));
    }
}
