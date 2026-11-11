package com.eventplatform.e2e.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Builds event dates relative to today in the API server time zone, so the scenarios never depend
 * on a fixed date that will eventually be in the past.
 */
public final class RelativeDates {

    private RelativeDates() {
    }

    public static LocalDateTime daysFromNowAt(int days, LocalTime time) {
        return LocalDate.now(TestConfiguration.serverTimeZone()).plusDays(days).atTime(time);
    }
}
