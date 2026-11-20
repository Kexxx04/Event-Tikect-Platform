package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.TestRows.START;
import static com.eventplatform.api.inmemory.TestRows.event;
import static com.eventplatform.api.inmemory.TestRows.eventStartingAt;
import static com.eventplatform.api.inmemory.TestRows.registration;
import static com.eventplatform.api.inmemory.TestRows.user;
import static com.eventplatform.api.model.enums.RegistrationStatus.CANCELLED;
import static com.eventplatform.api.model.enums.RegistrationStatus.CONFIRMED;
import static com.eventplatform.api.model.enums.RegistrationStatus.WAITING_LIST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

class InMemoryRegistrationRepositoryTests {

    private final InMemoryDatabase database = new InMemoryDatabase();
    private final InMemoryUserRepository users = new InMemoryUserRepository(database);
    private final InMemoryEventRepository events = new InMemoryEventRepository(database);
    private final InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository(database);

    private final Event event = events.save(event(10));
    private final Event otherEvent = events.save(event(10));
    private final User ana = users.save(user("ana"));
    private final User bob = users.save(user("bob"));
    private final User eva = users.save(user("eva"));
    private final User leo = users.save(user("leo"));

    private Registration register(User user, Event target, LocalDateTime date, RegistrationStatus status) {
        return registrations.save(registration(user, target, date, status));
    }

    // ---------------------------------------------------------------- constraints of V1

    @Test
    void rejectsASecondRegistrationOfTheSameUserForTheSameEvent() {
        register(ana, event, START, CANCELLED);

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> register(ana, event, START.plusDays(1), CONFIRMED));

        assertEquals("duplicate key value violates unique constraint \"uk_registrations_user_event\"",
                exception.getMessage());
        assertEquals(1, registrations.count());
    }

    @Test
    void allowsTheSameUserInAnotherEventAndAnotherUserInTheSameEvent() {
        Registration first = register(ana, event, START, CONFIRMED);

        assertDoesNotThrow(() -> register(ana, otherEvent, START, CONFIRMED));
        assertDoesNotThrow(() -> register(bob, event, START, CONFIRMED));
        first.setStatus(CANCELLED);
        assertDoesNotThrow(() -> registrations.save(first));
        assertEquals(3, registrations.count());
    }

    @Test
    void rejectsUsersAndEventsThatAreNotStoredLikeTheForeignKeys() {
        User unsaved = user("ghost");
        Event deletedEvent = events.save(event(5));
        events.deleteById(deletedEvent.getId());

        DataIntegrityViolationException missingUser = assertThrows(DataIntegrityViolationException.class,
                () -> register(unsaved, event, START, CONFIRMED));
        DataIntegrityViolationException missingEvent = assertThrows(DataIntegrityViolationException.class,
                () -> register(ana, deletedEvent, START, CONFIRMED));

        assertTrue(missingUser.getMessage().contains("fk_registrations_user"), missingUser.getMessage());
        assertTrue(missingEvent.getMessage().contains("fk_registrations_event"), missingEvent.getMessage());
        assertEquals(0, registrations.count());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullColumns")
    void rejectsNullsInNotNullColumns(String column, Consumer<Registration> clearColumn) {
        Registration registration = registration(ana, event, START, CONFIRMED);
        clearColumn.accept(registration);

        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> registrations.save(registration));

        assertEquals("null value in column \"" + column
                + "\" of relation \"registrations\" violates not-null constraint", exception.getMessage());
    }

    static Stream<Arguments> nullColumns() {
        return Stream.of(
                Arguments.of("user_id", (Consumer<Registration>) registration -> registration.setUser(null)),
                Arguments.of("event_id", (Consumer<Registration>) registration -> registration.setEvent(null)),
                Arguments.of("registration_date",
                        (Consumer<Registration>) registration -> registration.setRegistrationDate(null)),
                Arguments.of("status", (Consumer<Registration>) registration -> registration.setStatus(null)));
    }

    // ---------------------------------------------------------------- derived queries

    @Test
    void countsTheRegistrationsOfAnEventWithAStatus() {
        register(ana, event, START, CONFIRMED);
        register(bob, event, START, WAITING_LIST);
        register(eva, event, START, WAITING_LIST);
        register(leo, otherEvent, START, WAITING_LIST);

        assertEquals(1, registrations.countByEventIdAndStatus(event.getId(), CONFIRMED));
        assertEquals(2, registrations.countByEventIdAndStatus(event.getId(), WAITING_LIST));
        assertEquals(0, registrations.countByEventIdAndStatus(event.getId(), CANCELLED));
    }

    @Test
    void findsTheRegistrationOfAUserForAnEvent() {
        Registration registration = register(ana, event, START, CONFIRMED);
        register(ana, otherEvent, START, CONFIRMED);

        assertSame(registration, registrations.findByEventIdAndUserId(event.getId(), ana.getId()).orElseThrow());
        assertTrue(registrations.findByEventIdAndUserId(event.getId(), bob.getId()).isEmpty());
    }

    @Test
    void listsTheRegistrationsOfAnEventByRegistrationDateThenId() {
        // Saved out of order, with ana and leo registered at the same instant (the id breaks the tie).
        Registration eva3 = register(eva, event, START.plusMinutes(3), CONFIRMED);
        Registration ana1 = register(ana, event, START.plusMinutes(1), WAITING_LIST);
        Registration bob2 = register(bob, event, START.plusMinutes(2), CANCELLED);
        Registration leo1 = register(leo, event, START.plusMinutes(1), WAITING_LIST);
        register(ana, otherEvent, START, CONFIRMED);

        assertEquals(List.of(ana1, leo1, bob2, eva3),
                registrations.findByEventIdOrderByRegistrationDateAscIdAsc(event.getId()));
    }

    @Test
    void listsTheRegistrationsOfAnEventWithAStatusByRegistrationDateThenId() {
        Registration eva3 = register(eva, event, START.plusMinutes(3), WAITING_LIST);
        Registration ana1 = register(ana, event, START.plusMinutes(1), WAITING_LIST);
        register(bob, event, START, CONFIRMED);
        Registration leo1 = register(leo, event, START.plusMinutes(1), WAITING_LIST);
        register(ana, otherEvent, START, WAITING_LIST);

        assertEquals(List.of(ana1, leo1, eva3),
                registrations.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(event.getId(), WAITING_LIST));
    }

    @Test
    void appliesTheLimitAfterOrderingTheWaitlist() {
        Registration eva3 = register(eva, event, START.plusMinutes(3), WAITING_LIST);
        Registration ana1 = register(ana, event, START.plusMinutes(1), WAITING_LIST);
        Registration leo2 = register(leo, event, START.plusMinutes(2), WAITING_LIST);
        register(bob, event, START, CONFIRMED);
        Long eventId = event.getId();

        assertEquals(List.of(ana1), registrations.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                eventId, WAITING_LIST, Limit.of(1)));
        assertEquals(List.of(ana1, leo2), registrations.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                eventId, WAITING_LIST, Limit.of(2)));
        assertEquals(List.of(ana1, leo2, eva3), registrations.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                eventId, WAITING_LIST, Limit.of(10)));
        assertEquals(List.of(ana1, leo2, eva3), registrations.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                eventId, WAITING_LIST, Limit.unlimited()));
    }

    @Test
    void listsTheEventsOfAUserByEventStartDateThenRegistrationId() {
        Event later = events.save(eventStartingAt(START.plusMonths(2), 10));
        Event sameStart = events.save(event(10));
        Registration inLater = register(ana, later, START, CONFIRMED);
        Registration inEvent = register(ana, event, START.plusDays(1), CANCELLED);
        Registration inSameStart = register(ana, sameStart, START, WAITING_LIST);
        register(bob, event, START, CONFIRMED);

        assertEquals(List.of(inEvent, inSameStart, inLater),
                registrations.findByUserIdOrderByEventStartDateAscIdAsc(ana.getId()));
    }

    @Test
    void listsTheEventsOfAUserWithAStatusByEventStartDateThenRegistrationId() {
        Event later = events.save(eventStartingAt(START.plusMonths(2), 10));
        Registration inLater = register(ana, later, START, CONFIRMED);
        Registration inEvent = register(ana, event, START, CONFIRMED);
        register(ana, otherEvent, START, CANCELLED);
        register(bob, event, START, CONFIRMED);

        assertEquals(List.of(inEvent, inLater),
                registrations.findByUserIdAndStatusOrderByEventStartDateAscIdAsc(ana.getId(), CONFIRMED));
    }

    @Test
    void deletesRegistrationsInBatch() {
        Registration first = register(ana, event, START, CONFIRMED);
        Registration second = register(bob, event, START, CONFIRMED);
        Registration kept = register(eva, event, START, CONFIRMED);

        registrations.deleteAllInBatch(List.of(first, second));

        assertEquals(List.of(kept), registrations.findAll());
        // Without registrations, their users and events can be deleted.
        assertDoesNotThrow(() -> users.deleteAllByIdInBatch(List.of(ana.getId(), bob.getId())));
    }
}
