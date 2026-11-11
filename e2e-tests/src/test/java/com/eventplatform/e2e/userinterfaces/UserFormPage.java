package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /users/new (frontend/src/pages/users/UserFormPage.tsx). */
public final class UserFormPage {

    public static final Target NAME = Target.the("full name field").locatedBy("css:#name");
    public static final Target EMAIL = Target.the("email field").locatedBy("css:#email");
    public static final Target DOCUMENT = Target.the("document number field").locatedBy("css:#document");
    public static final Target SAVE = Target.the("create user button").locatedBy("css:form button[type='submit']");

    private UserFormPage() {
    }
}
