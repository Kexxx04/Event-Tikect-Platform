package com.eventplatform.e2e.questions;

import com.eventplatform.e2e.userinterfaces.AppShell;
import java.net.URI;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;
import net.serenitybdd.screenplay.questions.page.TheWebPage;

/** Where the browser is: the route of the URL and the title of the page header. */
public final class TheCurrentPage {

    private TheCurrentPage() {
    }

    /** Path of the current URL, e.g. "/login" or "/events". */
    public static Question<String> path() {
        return Question.about("the current page path")
                .answeredBy(actor -> URI.create(TheWebPage.currentUrl().answeredBy(actor)).getPath());
    }

    public static Question<String> title() {
        return Text.of(AppShell.PAGE_TITLE).describedAs("the page title");
    }
}
