package com.eventplatform.api.inmemory;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.time.LocalDateTime;

/** Valid rows for the contract tests of the in-memory repositories. */
final class TestRows {

    static final LocalDateTime START = LocalDateTime.of(2026, 3, 10, 18, 0);

    private TestRows() {
    }

    static User user(String key) {
        return new User("User " + key, key + "@example.com", "DOC-" + key);
    }

    static Event event(int capacity) {
        return eventStartingAt(START, capacity);
    }

    static Event eventStartingAt(LocalDateTime start, int capacity) {
        return new Event("Java Conf", null, start, start.plusHours(3), capacity, EventStatus.SCHEDULED);
    }

    static Registration registration(User user, Event event, LocalDateTime date, RegistrationStatus status) {
        return new Registration(user, event, date, status);
    }
}
