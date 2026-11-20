package com.eventplatform.api.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Event dates relative to today, so the tests keep passing as the calendar moves forward. The API compares
 * dates with LocalDateTime.now(clock) and the test profiles use the system clock of ClockConfig.
 */
public final class EventDates {

    // Feature files write future dates as "in 30 days at 18:00".
    private static final Pattern RELATIVE_DATE = Pattern.compile("in (\\d+) days? at (\\d{2}:\\d{2})");

    private EventDates() {
    }

    /** The given hour, a number of days from today. */
    public static LocalDateTime daysFromNow(int days, int hour) {
        return LocalDate.now().plusDays(days).atTime(hour, 0);
    }

    /** ISO format with seconds, the same one the API uses in its JSON responses. */
    public static String iso(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** Turns "in 30 days at 18:00" into an ISO date-time; any other value must already be one. */
    public static String resolve(String value) {
        Matcher relative = RELATIVE_DATE.matcher(value);
        if (!relative.matches()) {
            return iso(LocalDateTime.parse(value));
        }
        LocalDate day = LocalDate.now().plusDays(Integer.parseInt(relative.group(1)));
        return iso(day.atTime(LocalTime.parse(relative.group(2))));
    }
}
