package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.models.UserData;
import com.eventplatform.e2e.userinterfaces.UserListPage;
import com.eventplatform.e2e.utils.ActorMemory;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Creates a user and waits until the frontend returns to the users list; the actor remembers it by name. */
public class CreateUser implements Task {

    private final UserData user;
    private final String userName;

    public CreateUser(UserData user) {
        this.user = user;
        this.userName = user.name();
    }

    public static CreateUser with(UserData user) {
        return instrumented(CreateUser.class, user);
    }

    @Override
    @Step("{0} creates the user '#userName'")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                FillInTheUserForm.with(user),
                WaitUntil.the(UserListPage.SEARCH, isVisible()));
        ActorMemory.rememberUser(actor, user);
    }
}
