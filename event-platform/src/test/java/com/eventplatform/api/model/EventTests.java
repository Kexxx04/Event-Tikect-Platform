package com.eventplatform.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eventplatform.api.model.enums.EventStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EventTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 10, 18, 0);

    @Test
    void anEventCreatedWithoutStatusStartsScheduledAndEmpty() {
        Event event = new Event("Conference", "Talks", START, START.plusHours(2), 10, null);

        assertEquals(EventStatus.SCHEDULED, event.getStatus());
        assertEquals(0, event.getRegisteredCount());
    }

    @Test
    void anEventKeepsTheStatusItIsCreatedWith() {
        Event event = new Event("Conference", "Talks", START, START.plusHours(2), 10, EventStatus.ACTIVE);

        assertEquals(EventStatus.ACTIVE, event.getStatus());
    }
}
