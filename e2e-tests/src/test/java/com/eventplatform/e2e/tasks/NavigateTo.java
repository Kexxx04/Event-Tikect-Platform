package com.eventplatform.e2e.tasks;

import static net.serenitybdd.screenplay.matchers.WebElementStateMatchers.isVisible;

import com.eventplatform.e2e.userinterfaces.LoginPage;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Open;
import net.serenitybdd.screenplay.waits.WaitUntil;

/** Opens a page by its URL, relative to webdriver.base.url. */
public final class NavigateTo {

    private NavigateTo() {
    }

    public static Performable theLoginPage() {
        return Task.where("{0} opens the login page",
                Open.relativeUrl(LoginPage.PATH),
                WaitUntil.the(LoginPage.USERNAME, isVisible()));
    }

    /** Opens any route of the frontend without waiting for a specific page (it may redirect). */
    public static Performable thePath(String path) {
        return Task.where("{0} opens the page " + path, Open.relativeUrl(path));
    }
}
