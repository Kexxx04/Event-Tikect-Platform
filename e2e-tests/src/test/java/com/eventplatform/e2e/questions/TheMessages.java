package com.eventplatform.e2e.questions;

import com.eventplatform.e2e.userinterfaces.Notifications;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;

/** Error alert of the page and inline validation messages of the forms. */
public final class TheMessages {

    private TheMessages() {
    }

    public static Question<String> error() {
        return Text.of(Notifications.ERROR).describedAs("the error message");
    }

    /** Message under the input whose id is {@code field} (username, password, capacity, endDate...). */
    public static Question<String> errorOfTheField(String field) {
        return Text.of(Notifications.FIELD_ERROR.of(field)).describedAs("the error of the " + field + " field");
    }
}
