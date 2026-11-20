package com.eventplatform.api.inmemory;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Replaces PostgreSQL in the inmemory profile: one table per entity of V1__create_initial_schema.sql,
 * each with its rows in a ConcurrentHashMap and its IDENTITY sequence in an AtomicLong.
 *
 * <p>The registrations table also has the secondary indexes of the schema, so the queries by event or by
 * user only read the matching rows instead of the whole table: {@code idx_registrations_event},
 * {@code idx_registrations_user} and the unique {@code uk_registrations_user_event}.
 */
@Component
@Profile("inmemory")
public class InMemoryDatabase {

    private final Table<User> users = new Table<>();
    private final Table<Event> events = new Table<>();
    private final Table<Registration> registrations = new Table<>();

    private final Index<Registration, Long> registrationsByEvent =
            registrations.addIndex(registration -> registration.getEvent().getId());
    private final Index<Registration, Long> registrationsByUser =
            registrations.addIndex(registration -> registration.getUser().getId());
    private final Index<Registration, UserAndEvent> registrationsByUserAndEvent =
            registrations.addIndex(registration -> new UserAndEvent(
                    registration.getUser().getId(), registration.getEvent().getId()));

    Table<User> users() {
        return users;
    }

    Table<Event> events() {
        return events;
    }

    Table<Registration> registrations() {
        return registrations;
    }

    Index<Registration, Long> registrationsByEvent() {
        return registrationsByEvent;
    }

    Index<Registration, Long> registrationsByUser() {
        return registrationsByUser;
    }

    Index<Registration, UserAndEvent> registrationsByUserAndEvent() {
        return registrationsByUserAndEvent;
    }

    /** Deletes every row and restarts every sequence, so the next ids start at 1 again. */
    public void clear() {
        registrations.clear();
        users.clear();
        events.clear();
    }

    /** Key of {@code uk_registrations_user_event}. */
    record UserAndEvent(Long userId, Long eventId) {
    }

    /**
     * Rows of one table by id, the sequence of its IDENTITY column and its secondary indexes. Reads need no lock;
     * {@link #put} and {@link #remove} must run inside {@link #write}, which keeps rows and indexes consistent.
     */
    static final class Table<T> {

        private final Map<Long, T> rows = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        // Only filled while InMemoryDatabase is built, before any row exists.
        private final List<Index<T, ?>> indexes = new ArrayList<>();
        // Writes check the constraints and store the row as one atomic step, like a unique index does.
        private final Object writeLock = new Object();

        <K> Index<T, K> addIndex(Function<T, K> key) {
            Index<T, K> index = new Index<>(key);
            indexes.add(index);
            return index;
        }

        T get(Long id) {
            return rows.get(id);
        }

        boolean contains(Long id) {
            return rows.containsKey(id);
        }

        int size() {
            return rows.size();
        }

        Collection<T> rows() {
            return rows.values();
        }

        long nextId() {
            return sequence.incrementAndGet();
        }

        /** An explicit id never collides with the ids generated later. */
        void advanceSequenceTo(long id) {
            sequence.accumulateAndGet(id, Math::max);
        }

        /** Stores (or rewrites) the row and moves it to its current keys in every index. */
        void put(Long id, T row) {
            rows.put(id, row);
            indexes.forEach(index -> index.put(id, row));
        }

        void remove(Long id) {
            indexes.forEach(index -> index.remove(id));
            rows.remove(id);
        }

        void write(Runnable statement) {
            synchronized (writeLock) {
                statement.run();
            }
        }

        void clear() {
            synchronized (writeLock) {
                rows.clear();
                indexes.forEach(Index::clear);
                sequence.set(0);
            }
        }
    }

    /**
     * Secondary index: the ids of the rows by key. It remembers the key of each id, so rewriting a row whose key
     * changed moves it to the new key. Lookups are O(1) and return a live, concurrent set of ids.
     */
    static final class Index<T, K> {

        private final Function<T, K> key;
        private final Map<K, Set<Long>> idsByKey = new ConcurrentHashMap<>();
        private final Map<Long, K> keyById = new ConcurrentHashMap<>();

        private Index(Function<T, K> key) {
            this.key = key;
        }

        Set<Long> ids(K value) {
            return idsByKey.getOrDefault(value, Set.of());
        }

        private void put(Long id, T row) {
            K newKey = key.apply(row);
            K oldKey = keyById.put(id, newKey);
            if (oldKey != null && !oldKey.equals(newKey)) {
                removeId(oldKey, id);
            }
            idsByKey.computeIfAbsent(newKey, ignored -> ConcurrentHashMap.newKeySet()).add(id);
        }

        private void remove(Long id) {
            K oldKey = keyById.remove(id);
            if (oldKey != null) {
                removeId(oldKey, id);
            }
        }

        private void removeId(K oldKey, Long id) {
            idsByKey.computeIfPresent(oldKey, (ignored, ids) -> {
                ids.remove(id);
                return ids.isEmpty() ? null : ids;
            });
        }

        private void clear() {
            idsByKey.clear();
            keyById.clear();
        }
    }
}
