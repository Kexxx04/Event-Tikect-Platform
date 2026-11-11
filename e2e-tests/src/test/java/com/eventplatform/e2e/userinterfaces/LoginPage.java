package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /login (frontend/src/pages/LoginPage.tsx). */
public final class LoginPage {

    public static final String PATH = "/login";

    public static final Target USERNAME = Target.the("username field").locatedBy("css:#username");
    public static final Target PASSWORD = Target.the("password field").locatedBy("css:#password");
    public static final Target SIGN_IN = Target.the("sign in button").locatedBy("css:form button[type='submit']");

    private LoginPage() {
    }
}
