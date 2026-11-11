package com.eventplatform.e2e.utils;

import net.serenitybdd.screenplay.Consequence;
import net.serenitybdd.screenplay.EventualConsequence;

/**
 * The frontend reloads its data asynchronously after every action, so the assertions retry until they
 * pass or the timeout expires. Serenity's own default is the implicit wait (2 s), which is short on a
 * slow machine or while recording the screen.
 */
public final class Eventually {

    private static final long TIMEOUT_SECONDS = 10;

    private Eventually() {
    }

    public static <T> Consequence<T> eventually(Consequence<T> consequence) {
        return EventualConsequence.eventually(consequence).waitingForNoLongerThan(TIMEOUT_SECONDS).seconds();
    }
}
