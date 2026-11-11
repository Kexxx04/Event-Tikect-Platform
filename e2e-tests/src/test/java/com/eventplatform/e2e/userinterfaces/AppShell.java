package com.eventplatform.e2e.userinterfaces;

import net.serenitybdd.screenplay.targets.Target;

/** Sidebar shown on every authenticated page (frontend/src/components/Layout.tsx). */
public final class AppShell {

    public static final Target EVENTS_MENU = Target.the("Events menu option").locatedBy("css:nav[aria-label='Main'] a[href='/events']");
    public static final Target USERS_MENU = Target.the("Users menu option").locatedBy("css:nav[aria-label='Main'] a[href='/users']");
    public static final Target SIGNED_IN_USERNAME = Target.the("signed-in username").locatedBy("css:[data-testid='current-username']");
    public static final Target SIGN_OUT = Target.the("sign out button").locatedBy("css:button[aria-label='Sign out']");
    public static final Target PAGE_TITLE = Target.the("page title").locatedBy("css:main header h1");

    private AppShell() {
    }
}
