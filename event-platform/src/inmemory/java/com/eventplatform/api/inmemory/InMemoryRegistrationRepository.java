package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.SchemaConstraints.foreignKey;
import static com.eventplatform.api.inmemory.SchemaConstraints.notNull;
import static com.eventplatform.api.inmemory.SchemaConstraints.unique;

import com.eventplatform.api.inmemory.InMemoryDatabase.UserAndEvent;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.RegistrationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * Fake of {@link RegistrationRepository} over the registrations table of {@link InMemoryDatabase}. Each derived
 * query filters and orders exactly as its name says.
 *
 * <p>The queries never scan the whole table; they start from an index, like PostgreSQL with
 * {@code idx_registrations_event}, {@code idx_registrations_user} and {@code uk_registrations_user_event}.
 * With k registrations in the event and m registrations of the user:
 * <ul>
 *   <li>{@code findByEventIdAndUserId}, {@code save} (its unique and foreign key checks) and delete: O(1);</li>
 *   <li>{@code countByEventIdAndStatus}: O(k);</li>
 *   <li>{@code findByEventId...}: O(k log k), also with {@code Limit};</li>
 *   <li>{@code findByUserId...}: O(m log m).</li>
 * </ul>
 */
@Repository
@Profile("inmemory")
public class InMemoryRegistrationRepository extends AbstractInMemoryRepository<Registration>
        implements RegistrationRepository {

    // OrderByRegistrationDateAscIdAsc: the waitlist is served by registration date, then by id.
    private static final Comparator<Registration> BY_REGISTRATION_DATE_THEN_ID =
            Comparator.comparing(Registration::getRegistrationDate).thenComparing(Registration::getId);

    // OrderByEventStartDateAscIdAsc: by the start date of the event, then by the id of the registration.
    private static final Comparator<Registration> BY_EVENT_START_DATE_THEN_ID =
            Comparator.comparing((Registration registration) -> registration.getEvent().getStartDate())
                    .thenComparing(Registration::getId);

    private final InMemoryDatabase database;

    public InMemoryRegistrationRepository(InMemoryDatabase database) {
        super(database.registrations(), Registration.class);
        this.database = database;
    }

    @Override
    public long countByEventIdAndStatus(Long eventId, RegistrationStatus status) {
        return ofEvent(eventId).filter(withStatus(status)).count();
    }

    @Override
    public Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId) {
        List<Registration> found = query(ofUserAndEvent(userId, eventId), row -> true, BY_REGISTRATION_DATE_THEN_ID,
                Long.MAX_VALUE);
        if (found.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, found.size());
        }
        return found.stream().findFirst();
    }

    @Override
    public List<Registration> findByEventIdOrderByRegistrationDateAscIdAsc(Long eventId) {
        return query(ofEvent(eventId), row -> true, BY_REGISTRATION_DATE_THEN_ID, Long.MAX_VALUE);
    }

    @Override
    public List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
            Long eventId, RegistrationStatus status) {
        return query(ofEvent(eventId), withStatus(status), BY_REGISTRATION_DATE_THEN_ID, Long.MAX_VALUE);
    }

    @Override
    public List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
            Long eventId, RegistrationStatus status, Limit limit) {
        Assert.notNull(limit, "Limit must not be null; use Limit.unlimited()");
        long maxResults = limit.isLimited() ? limit.max() : Long.MAX_VALUE;
        return query(ofEvent(eventId), withStatus(status), BY_REGISTRATION_DATE_THEN_ID, maxResults);
    }

    @Override
    public List<Registration> findByUserIdOrderByEventStartDateAscIdAsc(Long userId) {
        return query(ofUser(userId), row -> true, BY_EVENT_START_DATE_THEN_ID, Long.MAX_VALUE);
    }

    @Override
    public List<Registration> findByUserIdAndStatusOrderByEventStartDateAscIdAsc(
            Long userId, RegistrationStatus status) {
        return query(ofUser(userId), withStatus(status), BY_EVENT_START_DATE_THEN_ID, Long.MAX_VALUE);
    }

    /** NOT NULL columns, the foreign keys to users and events, and uk_registrations_user_event. */
    @Override
    protected void checkConstraints(Registration registration) {
        notNull(registration.getUser(), "registrations", "user_id");
        notNull(registration.getEvent(), "registrations", "event_id");
        notNull(registration.getRegistrationDate(), "registrations", "registration_date");
        notNull(registration.getStatus(), "registrations", "status");
        Long userId = registration.getUser().getId();
        Long eventId = registration.getEvent().getId();
        foreignKey(userId != null && database.users().contains(userId), "registrations", "fk_registrations_user");
        foreignKey(eventId != null && database.events().contains(eventId), "registrations", "fk_registrations_event");
        Long ownId = idOf(registration);
        boolean taken = database.registrationsByUserAndEvent().ids(new UserAndEvent(userId, eventId)).stream()
                .anyMatch(id -> !id.equals(ownId));
        unique(taken, "uk_registrations_user_event");
    }

    // The indexes give the candidates; the predicate checks them again in case a row changed meanwhile.
    private Stream<Registration> ofEvent(Long eventId) {
        return rowsWithIds(database.registrationsByEvent().ids(eventId))
                .filter(registration -> Objects.equals(registration.getEvent().getId(), eventId));
    }

    private Stream<Registration> ofUser(Long userId) {
        return rowsWithIds(database.registrationsByUser().ids(userId))
                .filter(registration -> Objects.equals(registration.getUser().getId(), userId));
    }

    private Stream<Registration> ofUserAndEvent(Long userId, Long eventId) {
        return rowsWithIds(database.registrationsByUserAndEvent().ids(new UserAndEvent(userId, eventId)))
                .filter(registration -> Objects.equals(registration.getUser().getId(), userId)
                        && Objects.equals(registration.getEvent().getId(), eventId));
    }

    private static Predicate<Registration> withStatus(RegistrationStatus status) {
        return registration -> registration.getStatus() == status;
    }
}
