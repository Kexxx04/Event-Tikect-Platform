package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.TestRows.START;
import static com.eventplatform.api.inmemory.TestRows.event;
import static com.eventplatform.api.inmemory.TestRows.registration;
import static com.eventplatform.api.inmemory.TestRows.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eventplatform.api.inmemory.InMemoryDatabase.UserAndEvent;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryDatabaseTests {

    private final InMemoryDatabase database = new InMemoryDatabase();
    private final InMemoryUserRepository users = new InMemoryUserRepository(database);
    private final InMemoryEventRepository events = new InMemoryEventRepository(database);
    private final InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository(database);

    @Test
    void clearDeletesEveryRowAndRestartsEverySequence() {
        users.saveAll(List.of(user("a"), user("b")));
        Event event = events.save(event(10));
        registrations.save(registration(users.findById(1L).orElseThrow(), event, START, RegistrationStatus.CONFIRMED));

        database.clear();

        assertEquals(0, users.count());
        assertEquals(0, events.count());
        assertEquals(0, registrations.count());
        assertFalse(users.existsByEmailIgnoreCase("a@example.com"));
        User user = users.save(user("c"));
        Event newEvent = events.save(event(5));
        assertEquals(1L, user.getId());
        assertEquals(1L, newEvent.getId());
        assertEquals(1L, registrations.save(
                registration(user, newEvent, START, RegistrationStatus.CONFIRMED)).getId());
    }

    @Test
    void indexesTheRegistrationsByEventByUserAndByBoth() {
        User ana = users.save(user("ana"));
        User bob = users.save(user("bob"));
        Event first = events.save(event(10));
        Event second = events.save(event(10));
        Registration anaFirst = registrations.save(registration(ana, first, START, RegistrationStatus.CONFIRMED));
        Registration bobFirst = registrations.save(registration(bob, first, START, RegistrationStatus.CONFIRMED));
        Registration anaSecond = registrations.save(registration(ana, second, START, RegistrationStatus.WAITING_LIST));

        assertEquals(Set.of(anaFirst.getId(), bobFirst.getId()), database.registrationsByEvent().ids(first.getId()));
        assertEquals(Set.of(anaSecond.getId()), database.registrationsByEvent().ids(second.getId()));
        assertEquals(Set.of(anaFirst.getId(), anaSecond.getId()), database.registrationsByUser().ids(ana.getId()));
        assertEquals(Set.of(bobFirst.getId()),
                database.registrationsByUserAndEvent().ids(new UserAndEvent(bob.getId(), first.getId())));
        assertEquals(Set.of(), database.registrationsByUserAndEvent().ids(new UserAndEvent(bob.getId(), second.getId())));
    }

    @Test
    void movingDeletingAndClearingRowsKeepsTheIndexesInSync() {
        User ana = users.save(user("ana"));
        User bob = users.save(user("bob"));
        Event first = events.save(event(10));
        Event second = events.save(event(10));
        Registration anaFirst = registrations.save(registration(ana, first, START, RegistrationStatus.CONFIRMED));
        Registration bobFirst = registrations.save(registration(bob, first, START, RegistrationStatus.CONFIRMED));

        bobFirst.setEvent(second);
        registrations.save(bobFirst);
        assertEquals(Set.of(anaFirst.getId()), database.registrationsByEvent().ids(first.getId()));
        assertEquals(Set.of(bobFirst.getId()), database.registrationsByEvent().ids(second.getId()));
        assertEquals(Set.of(bobFirst.getId()),
                database.registrationsByUserAndEvent().ids(new UserAndEvent(bob.getId(), second.getId())));
        assertEquals(Set.of(), database.registrationsByUserAndEvent().ids(new UserAndEvent(bob.getId(), first.getId())));

        registrations.deleteAllInBatch(List.of(anaFirst));
        assertEquals(Set.of(), database.registrationsByEvent().ids(first.getId()));
        assertEquals(Set.of(), database.registrationsByUser().ids(ana.getId()));

        database.clear();
        assertEquals(Set.of(), database.registrationsByEvent().ids(second.getId()));
        assertEquals(Set.of(), database.registrationsByUser().ids(bob.getId()));
    }

    @Test
    void eachTableHasItsOwnSequence() {
        assertEquals(1L, users.save(user("a")).getId());
        assertEquals(2L, users.save(user("b")).getId());
        assertEquals(1L, events.save(event(10)).getId());
    }
}
