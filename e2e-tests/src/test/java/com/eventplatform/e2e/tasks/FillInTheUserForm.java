package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.userinterfaces.AppShell;
import com.eventplatform.e2e.userinterfaces.UserFormPage;
import com.eventplatform.e2e.userinterfaces.UserListPage;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Goes to Users > New user, fills in the form and submits. Used for valid and duplicated data. */
public class FillInTheUserForm implements Task {

    private final UserData user;
    private final String userName;
    private final String email;

    public FillInTheUserForm(UserData user) {
        this.user = user;
        this.userName = user.name();
        this.email = user.email();
    }

    public static FillInTheUserForm with(UserData user) {
        return instrumented(FillInTheUserForm.class, user);
    }

    @Override
    @Step("{0} submits the new user form for '#userName' (#email)")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                Click.on(AppShell.USERS_MENU),
                Click.on(UserListPage.NEW_USER),
                WaitUntil.the(UserFormPage.NAME, isVisible()),
                Enter.theValue(user.name()).into(UserFormPage.NAME),
                Enter.theValue(user.email()).into(UserFormPage.EMAIL),
                Enter.theValue(user.document()).into(UserFormPage.DOCUMENT),
                Click.on(UserFormPage.SAVE));
    }
}
