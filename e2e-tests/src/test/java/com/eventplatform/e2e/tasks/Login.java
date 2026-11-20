package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.Tasks.instrumented;
import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.interactions.EnterSecret;
import com.eventplatform.e2e.userinterfaces.LoginPage;
import com.eventplatform.e2e.utils.TestConfiguration;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.actions.Enter;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Fills in and submits the login form. It does not check the result: valid and invalid logins use it. */
public class Login implements Task {

    private final String username;
    private final String password;

    public Login(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /** The administrator configured in serenity.conf (E2E_ADMIN_USERNAME / E2E_ADMIN_PASSWORD). */
    public static Login withConfiguredCredentials() {
        return withCredentials(TestConfiguration.adminUsername(), TestConfiguration.adminPassword());
    }

    public static Login withCredentials(String username, String password) {
        return instrumented(Login.class, username, password);
    }

    @Override
    @Step("{0} signs in with the username '#username'")
    public <T extends Actor> void performAs(T actor) {
        actor.attemptsTo(
                WaitUntil.the(LoginPage.USERNAME, isVisible()),
                Enter.theValue(username).into(LoginPage.USERNAME),
                EnterSecret.value(password).into(LoginPage.PASSWORD),
                Click.on(LoginPage.SIGN_IN));
    }
}
