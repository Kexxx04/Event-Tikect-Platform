package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.userinterfaces.AppShell;
import com.eventplatform.e2e.userinterfaces.LoginPage;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Signs out from the sidebar; the frontend only removes the token from the browser. */
public final class SignOut {

    private SignOut() {
    }

    public static Performable fromTheSidebar() {
        return Task.where("{0} signs out",
                Click.on(AppShell.SIGN_OUT),
                WaitUntil.the(LoginPage.USERNAME, isVisible()));
    }
}
