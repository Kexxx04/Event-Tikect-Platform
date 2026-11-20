package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.SchemaConstraints.check;
import static com.eventplatform.api.inmemory.SchemaConstraints.notNull;
import static com.eventplatform.api.inmemory.SchemaConstraints.notReferenced;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.repository.EventRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fake of {@link EventRepository} over the events table of {@link InMemoryDatabase}. */
@Repository
@Profile("inmemory")
public class InMemoryEventRepository extends AbstractInMemoryRepository<Event> implements EventRepository {

    // One lock per event id, created on first use. They survive InMemoryDatabase.clear(): a lock that nobody
    // holds is simply reused by the next event with the same id.
    private final Map<Long, ReentrantLock> rowLocks = new ConcurrentHashMap<>();
    private final InMemoryDatabase database;

    public InMemoryEventRepository(InMemoryDatabase database) {
        super(database.events(), Event.class);
        this.database = database;
    }

    /**
     * Emulates {@code SELECT ... FOR UPDATE} (PESSIMISTIC_WRITE). Inside a transaction, the row lock of the
     * event is acquired once and held until the transaction ends (afterCompletion of a
     * TransactionSynchronization), so concurrent transactions on the same event run one after another while
     * different events never block each other. Outside a transaction, the lock only lasts for this call,
     * like an auto-committed statement. A missing event returns empty without locking anything.
     */
    @Override
    public Optional<Event> findByIdForUpdate(Long id) {
        if (!existsById(id)) {
            return Optional.empty();
        }
        ReentrantLock lock = rowLocks.computeIfAbsent(id, key -> new ReentrantLock());
        if (isTransactionActive()) {
            HeldRowLocks held = currentSynchronization(HeldRowLocks.class, locks -> locks.repository == this,
                    () -> new HeldRowLocks(this));
            held.acquireOnce(id, lock);
        } else {
            acquire(id, lock);
            lock.unlock();
        }
        // Read after waiting, so the caller sees what the previous lock holder committed.
        return findById(id);
    }

    /** NOT NULL columns and the CHECK constraints chk_events_dates, chk_events_capacity and chk_events_registered_count. */
    @Override
    protected void checkConstraints(Event event) {
        notNull(event.getName(), "events", "name");
        notNull(event.getStartDate(), "events", "start_date");
        notNull(event.getEndDate(), "events", "end_date");
        notNull(event.getCapacity(), "events", "capacity");
        notNull(event.getRegisteredCount(), "events", "registered_count");
        notNull(event.getStatus(), "events", "status");
        check(event.getEndDate().isAfter(event.getStartDate()), "events", "chk_events_dates");
        check(event.getCapacity() > 0, "events", "chk_events_capacity");
        check(event.getRegisteredCount() >= 0 && event.getRegisteredCount() <= event.getCapacity(),
                "events", "chk_events_registered_count");
    }

    @Override
    protected void checkNotReferenced(Long id) {
        boolean registered = database.registrations().rows().values().stream()
                .anyMatch(registration -> id.equals(registration.getEvent().getId()));
        notReferenced(registered, "events", "fk_registrations_event", "registrations");
    }

    private static boolean isTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private static void acquire(Long id, ReentrantLock lock) {
        try {
            // Like PostgreSQL without lock_timeout: wait as long as the other transaction needs.
            lock.lockInterruptibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("Interrupted while waiting for the lock of event " + id, exception);
        }
    }

    /** Row locks held by one transaction; it is bound to the thread that runs that transaction. */
    private static final class HeldRowLocks implements TransactionSynchronization {

        private final InMemoryEventRepository repository;
        private final Map<Long, ReentrantLock> locks = new LinkedHashMap<>();

        private HeldRowLocks(InMemoryEventRepository repository) {
            this.repository = repository;
        }

        /** A transaction that already holds the lock does not take it again (ReentrantLock would count it twice). */
        private void acquireOnce(Long id, ReentrantLock lock) {
            if (!locks.containsKey(id)) {
                acquire(id, lock);
                locks.put(id, lock);
            }
        }

        @Override
        public void afterCompletion(int status) {
            // Commit or rollback: the database releases the row locks when the transaction ends.
            List<ReentrantLock> acquired = new ArrayList<>(locks.values());
            Collections.reverse(acquired);
            acquired.forEach(ReentrantLock::unlock);
            locks.clear();
        }
    }
}
