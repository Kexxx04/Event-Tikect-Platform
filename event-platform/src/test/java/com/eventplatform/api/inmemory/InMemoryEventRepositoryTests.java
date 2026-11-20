package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.TestRows.START;
import static com.eventplatform.api.inmemory.TestRows.event;
import static com.eventplatform.api.inmemory.TestRows.registration;
import static com.eventplatform.api.inmemory.TestRows.user;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class InMemoryEventRepositoryTests {

    private final InMemoryDatabase database = new InMemoryDatabase();
    private final InMemoryEventRepository events = new InMemoryEventRepository(database);
    private final TransactionTemplate transaction = new TransactionTemplate(new InMemoryTransactionManager());

    // ---------------------------------------------------------------- constraints of V1

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsACapacityThatIsNotPositive(int capacity) {
        Event event = event(10);
        event.setCapacity(capacity);

        assertViolates("chk_events_capacity", event);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsAnEndDateThatIsNotAfterTheStartDate(int hoursAfterStart) {
        Event event = event(10);
        event.setEndDate(event.getStartDate().plusHours(hoursAfterStart));

        assertViolates("chk_events_dates", event);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 11})
    void rejectsARegisteredCountOutsideZeroAndTheCapacity(int registeredCount) {
        Event event = event(10);
        event.setRegisteredCount(registeredCount);

        assertViolates("chk_events_registered_count", event);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    void acceptsARegisteredCountFromZeroUpToTheCapacity(int registeredCount) {
        Event event = event(10);
        event.setRegisteredCount(registeredCount);

        assertDoesNotThrow(() -> events.save(event));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullColumns")
    void rejectsNullsInNotNullColumns(String column, Consumer<Event> clearColumn) {
        Event event = event(10);
        clearColumn.accept(event);

        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> events.save(event));

        assertEquals("null value in column \"" + column + "\" of relation \"events\" violates not-null constraint",
                exception.getMessage());
    }

    static Stream<Arguments> nullColumns() {
        return Stream.of(
                Arguments.of("name", (Consumer<Event>) event -> event.setName(null)),
                Arguments.of("start_date", (Consumer<Event>) event -> event.setStartDate(null)),
                Arguments.of("end_date", (Consumer<Event>) event -> event.setEndDate(null)),
                Arguments.of("capacity", (Consumer<Event>) event -> event.setCapacity(null)),
                Arguments.of("registered_count", (Consumer<Event>) event -> event.setRegisteredCount(null)),
                Arguments.of("status", (Consumer<Event>) event -> event.setStatus(null)));
    }

    @Test
    void checksTheConstraintsOfTheEventsThatATransactionChangedBeforeItCommits() {
        long id = events.save(event(2)).getId();

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> transaction.executeWithoutResult(status ->
                        events.findByIdForUpdate(id).orElseThrow().setRegisteredCount(3)));

        assertTrue(exception.getMessage().contains("chk_events_registered_count"), exception.getMessage());
    }

    @Test
    void doesNotCheckTheRowsThatATransactionOnlyReadBecauseHibernateOnlyFlushesChanges() {
        Event event = events.save(event(2));
        // Changed outside any transaction: nothing flushes it, so the row keeps the invalid value.
        event.setRegisteredCount(3);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status ->
                events.findByIdForUpdate(event.getId()).orElseThrow().getName()));
    }

    @Test
    void doesNotCheckReadOnlyTransactionsBecauseHibernateDoesNotFlushThem() {
        long id = events.save(event(2)).getId();
        transaction.setReadOnly(true);

        assertDoesNotThrow(() -> transaction.executeWithoutResult(status ->
                events.findById(id).orElseThrow().setCapacity(0)));
    }

    @Test
    void refusesToDeleteAnEventWithRegistrationsLikeTheForeignKey() {
        InMemoryUserRepository users = new InMemoryUserRepository(database);
        InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository(database);
        Event event = events.save(event(10));
        registrations.save(registration(users.save(user("ada")), event, START, RegistrationStatus.CONFIRMED));

        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> events.deleteById(event.getId()));

        assertTrue(exception.getMessage().contains("fk_registrations_event"), exception.getMessage());
        assertTrue(events.existsById(event.getId()));
    }

    @Test
    void findByIdForUpdateReturnsTheStoredEventOrEmpty() {
        Event event = events.save(event(10));

        assertSame(event, events.findByIdForUpdate(event.getId()).orElseThrow());
        assertTrue(events.findByIdForUpdate(99L).isEmpty());
    }

    private void assertViolates(String constraint, Event event) {
        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> events.save(event));
        assertEquals("new row for relation \"events\" violates check constraint \"" + constraint + "\"",
                exception.getMessage());
        assertEquals(0, events.count());
    }

    // ---------------------------------------------------------------- pessimistic lock of findByIdForUpdate

    @Nested
    class PessimisticLock {

        private final ExecutorService threads = Executors.newFixedThreadPool(2);
        private final CountDownLatch locked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @AfterEach
        void stopThreads() {
            release.countDown();
            threads.shutdownNow();
        }

        /** On another thread, a transaction locks the event and keeps the lock until {@code release}. */
        private Future<?> holdTheLockOf(long eventId, Consumer<Event> change) {
            return threads.submit(() -> transaction.executeWithoutResult(status -> {
                change.accept(events.findByIdForUpdate(eventId).orElseThrow());
                locked.countDown();
                awaitQuietly(release);
            }));
        }

        private Future<?> lockInANewTransaction(long eventId) {
            return threads.submit(() -> transaction.executeWithoutResult(status -> events.findByIdForUpdate(eventId)));
        }

        @Test
        void twoTransactionsOnTheSameEventRunOneAfterTheOther() throws Exception {
            long id = events.save(event(10)).getId();
            Future<?> first = holdTheLockOf(id, event -> event.setRegisteredCount(1));
            assertTrue(locked.await(5, SECONDS));

            AtomicInteger seenBySecond = new AtomicInteger(-1);
            CountDownLatch secondLocked = new CountDownLatch(1);
            Future<?> second = threads.submit(() -> transaction.executeWithoutResult(status -> {
                seenBySecond.set(events.findByIdForUpdate(id).orElseThrow().getRegisteredCount());
                secondLocked.countDown();
            }));

            assertFalse(secondLocked.await(300, MILLISECONDS), "the second transaction must wait for the first");
            release.countDown();
            first.get(5, SECONDS);
            second.get(5, SECONDS);
            assertEquals(1, seenBySecond.get(), "the second transaction reads what the first one committed");
        }

        @Test
        void differentEventsDoNotBlockEachOther() throws Exception {
            long held = events.save(event(10)).getId();
            long other = events.save(event(10)).getId();
            Future<?> first = holdTheLockOf(held, event -> { });
            assertTrue(locked.await(5, SECONDS));

            lockInANewTransaction(other).get(5, SECONDS);

            release.countDown();
            first.get(5, SECONDS);
        }

        @Test
        void aTransactionTakesTheLockOnceAndReleasesItWhenItCommits() throws Exception {
            long id = events.save(event(10)).getId();

            transaction.executeWithoutResult(status -> {
                events.findByIdForUpdate(id);
                events.findByIdForUpdate(id);
            });

            // A lock taken twice and released once would keep this thread waiting forever.
            lockInANewTransaction(id).get(5, SECONDS);
        }

        @Test
        void theLockIsAlsoReleasedWhenTheTransactionRollsBack() throws Exception {
            long id = events.save(event(10)).getId();

            assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
                events.findByIdForUpdate(id);
                throw new IllegalStateException("business rule violated");
            }));

            lockInANewTransaction(id).get(5, SECONDS);
        }

        @Test
        void withoutATransactionTheLockOnlyLastsForTheCallButStillWaitsForTheHolder() throws Exception {
            long id = events.save(event(10)).getId();
            events.findByIdForUpdate(id);
            lockInANewTransaction(id).get(5, SECONDS);

            Future<?> first = holdTheLockOf(id, event -> { });
            assertTrue(locked.await(5, SECONDS));
            Future<?> withoutTransaction = threads.submit(() -> events.findByIdForUpdate(id));

            assertThrows(TimeoutException.class, () -> withoutTransaction.get(300, MILLISECONDS));
            release.countDown();
            first.get(5, SECONDS);
            withoutTransaction.get(5, SECONDS);
        }

        @Test
        void aTransactionInterruptedWhileWaitingFailsWithCannotAcquireLock() throws Exception {
            long id = events.save(event(10)).getId();
            Future<?> first = holdTheLockOf(id, event -> { });
            assertTrue(locked.await(5, SECONDS));

            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread waiting = new Thread(() -> {
                try {
                    transaction.executeWithoutResult(status -> events.findByIdForUpdate(id));
                } catch (RuntimeException exception) {
                    failure.set(exception);
                }
            });
            waiting.start();
            waiting.interrupt();
            waiting.join(5_000);

            assertInstanceOf(CannotAcquireLockException.class, failure.get());
            release.countDown();
            first.get(5, SECONDS);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
