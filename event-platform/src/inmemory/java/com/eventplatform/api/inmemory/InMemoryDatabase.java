package com.eventplatform.api.inmemory;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Replaces PostgreSQL in the inmemory profile: one table per entity of V1__create_initial_schema.sql,
 * each with its rows in a ConcurrentHashMap and its IDENTITY sequence in an AtomicLong.
 */
@Component
@Profile("inmemory")
public class InMemoryDatabase {

    private final Table<User> users = new Table<>();
    private final Table<Event> events = new Table<>();
    private final Table<Registration> registrations = new Table<>();

    Table<User> users() {
        return users;
    }

    Table<Event> events() {
        return events;
    }

    Table<Registration> registrations() {
        return registrations;
    }

    /** Deletes every row and restarts every sequence, so the next ids start at 1 again. */
    public void clear() {
        registrations.clear();
        users.clear();
        events.clear();
    }

    /** Rows of one table by id and the sequence of its IDENTITY column. */
    static final class Table<T> {

        private final Map<Long, T> rows = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        // Writes check the constraints and store the row as one atomic step, like a unique index does.
        private final Object writeLock = new Object();

        Map<Long, T> rows() {
            return rows;
        }

        long nextId() {
            return sequence.incrementAndGet();
        }

        /** An explicit id never collides with the ids generated later. */
        void advanceSequenceTo(long id) {
            sequence.accumulateAndGet(id, Math::max);
        }

        void write(Runnable statement) {
            synchronized (writeLock) {
                statement.run();
            }
        }

        void clear() {
            synchronized (writeLock) {
                rows.clear();
                sequence.set(0);
            }
        }
    }
}
