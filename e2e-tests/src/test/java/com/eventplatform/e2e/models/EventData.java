package com.eventplatform.e2e.models;

import com.eventplatform.e2e.utils.RelativeDates;
import com.eventplatform.e2e.utils.UniqueData;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * An event typed into the "New event" form. The name gets a unique suffix so the event can be found
 * in the events list, and the capacity is text so invalid values (0, -5) can be typed too.
 */
public record EventData(String name, String description, LocalDateTime startDate, LocalDateTime endDate, String capacity) {

    private static final LocalTime START_TIME = LocalTime.of(18, 0);
    private static final LocalTime END_TIME = LocalTime.of(21, 0);

    /** An event that starts at 18:00 in {@code days} days and ends the same day at 21:00. */
    public static EventData called(String name, int capacity, int days) {
        return between(name, String.valueOf(capacity), days, days);
    }

    /** Starts at 18:00 in {@code startDays} days and ends at 21:00 in {@code endDays} days. */
    public static EventData between(String name, String capacity, int startDays, int endDays) {
        return new EventData(
                name + " " + UniqueData.suffix(),
                "Created by the Serenity end-to-end tests",
                RelativeDates.daysFromNowAt(startDays, START_TIME),
                RelativeDates.daysFromNowAt(endDays, endDays == startDays ? END_TIME : START_TIME),
                capacity);
    }
}
