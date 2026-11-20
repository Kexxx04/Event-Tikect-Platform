package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.SchemaConstraints.foreignKey;
import static com.eventplatform.api.inmemory.SchemaConstraints.notNull;
import static com.eventplatform.api.inmemory.SchemaConstraints.unique;

import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.RegistrationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

/**
 * Fake of {@link RegistrationRepository} over the registrations table of {@link InMemoryDatabase}. Each derived
 * query filters and orders exactly as its name says.
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
        return rows().filter(forEvent(eventId).and(withStatus(status))).count();
    }

    @Override
    public Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId) {
        List<Registration> found = query(forEvent(eventId).and(forUser(userId)), BY_REGISTRATION_DATE_THEN_ID);
        if (found.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, found.size());
        }
        return found.stream().findFirst();
    }

    @Override
    public List<Registration> findByEventIdOrderByRegistrationDateAscIdAsc(Long eventId) {
        return query(forEvent(eventId), BY_REGISTRATION_DATE_THEN_ID);
    }

    @Override
    public List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
            Long eventId, RegistrationStatus status) {
        return query(forEvent(eventId).and(withStatus(status)), BY_REGISTRATION_DATE_THEN_ID);
    }

    @Override
    public List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
            Long eventId, RegistrationStatus status, Limit limit) {
        Assert.notNull(limit, "Limit must not be null; use Limit.unlimited()");
        long maxResults = limit.isLimited() ? limit.max() : Long.MAX_VALUE;
        return query(forEvent(eventId).and(withStatus(status)), BY_REGISTRATION_DATE_THEN_ID, maxResults);
    }

    @Override
    public List<Registration> findByUserIdOrderByEventStartDateAscIdAsc(Long userId) {
        return query(forUser(userId), BY_EVENT_START_DATE_THEN_ID);
    }

    @Override
    public List<Registration> findByUserIdAndStatusOrderByEventStartDateAscIdAsc(
            Long userId, RegistrationStatus status) {
        return query(forUser(userId).and(withStatus(status)), BY_EVENT_START_DATE_THEN_ID);
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
        foreignKey(userId != null && database.users().rows().containsKey(userId),
                "registrations", "fk_registrations_user");
        foreignKey(eventId != null && database.events().rows().containsKey(eventId),
                "registrations", "fk_registrations_event");
        unique(otherRows(registration).anyMatch(forEvent(eventId).and(forUser(userId))),
                "uk_registrations_user_event");
    }

    private static Predicate<Registration> forEvent(Long eventId) {
        return registration -> Objects.equals(registration.getEvent().getId(), eventId);
    }

    private static Predicate<Registration> forUser(Long userId) {
        return registration -> Objects.equals(registration.getUser().getId(), userId);
    }

    private static Predicate<Registration> withStatus(RegistrationStatus status) {
        return registration -> registration.getStatus() == status;
    }
}
