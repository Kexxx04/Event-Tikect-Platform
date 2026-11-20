package com.eventplatform.e2e.stepdefinitions;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.actors.OnStage;
import net.serenitybdd.screenplay.actors.OnlineCast;

/** Prepares the Screenplay stage: every actor gets a browser managed by Serenity. */
public class StageHooks {

    private static final String ADMINISTRATOR = "Administrator";

    @Before(order = 0)
    public void setTheStage() {
        OnStage.setTheStage(new OnlineCast());
    }

    @After
    public void drawTheCurtain() {
        OnStage.drawTheCurtain();
    }

    /** The only role of the application: the administrator configured in the API. */
    static Actor administrator() {
        return OnStage.theActorCalled(ADMINISTRATOR);
    }
}
