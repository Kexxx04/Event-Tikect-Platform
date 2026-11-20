package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** /users (frontend/src/pages/users/UsersPage.tsx). */
public final class UserListPage {

    public static final Target NEW_USER = Target.the("New user button").locatedBy("css:a[href='/users/new']");
    public static final Target SEARCH = Target.the("user search field").locatedBy("css:#user-search");

    private UserListPage() {
    }
}
