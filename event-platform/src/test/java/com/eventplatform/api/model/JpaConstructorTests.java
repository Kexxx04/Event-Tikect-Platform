package com.eventplatform.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.UserStatus;
import org.junit.jupiter.api.Test;

/**
 * Hibernate creates the entities it reads with their no-argument constructors. The in-memory repositories
 * return the stored instances instead, so these tests keep the defaults of those constructors covered.
 */
class JpaConstructorTests {

    @Test
    void aUserStartsActive() {
        User user = new User();

        assertNull(user.getId());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    void anEventStartsScheduledWithoutRegistrations() {
        Event event = new Event();

        assertNull(event.getId());
        assertEquals(0, event.getRegisteredCount());
        assertEquals(EventStatus.SCHEDULED, event.getStatus());
    }

    @Test
    void aRegistrationHasNoDefaultStatusBecauseTheServiceAlwaysSetsIt() {
        Registration registration = new Registration();

        assertNull(registration.getId());
        assertNull(registration.getStatus());
        assertNull(registration.getRegistrationDate());
    }
}
