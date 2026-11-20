package com.eventplatform.e2e.questions;

import com.eventplatform.e2e.userinterfaces.AppShell;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;

/** Username shown at the bottom of the sidebar while there is a session. */
public final class TheSignedInUser {

    private TheSignedInUser() {
    }

    public static Question<String> name() {
        return Text.of(AppShell.SIGNED_IN_USERNAME).describedAs("the signed-in username");
    }
}
