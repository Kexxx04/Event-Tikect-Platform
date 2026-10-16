package com.eventplatform.api.service;

import com.eventplatform.api.dto.registration.RegistrationCancellationResponse;
import com.eventplatform.api.dto.registration.RegistrationResponse;
import com.eventplatform.api.dto.registration.UserEventResponse;
import com.eventplatform.api.dto.registration.WaitlistEntryResponse;
import com.eventplatform.api.exception.BusinessRuleViolationException;
import com.eventplatform.api.exception.EventNotFoundException;
import com.eventplatform.api.exception.RegistrationNotFoundException;
import com.eventplatform.api.exception.UserNotFoundException;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.model.enums.UserStatus;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.RegistrationRepository;
import com.eventplatform.api.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RegistrationService {

    private static final Set<RegistrationStatus> ACTIVE_STATUSES =
            EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITING_LIST);

    private static final Set<EventStatus> CLOSED_STATUSES = EnumSet.of(EventStatus.FINISHED, EventStatus.CANCELLED);

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final WaitlistService waitlistService;
    private final Clock clock;

    public RegistrationService(
            EventRepository eventRepository,
            UserRepository userRepository,
            RegistrationRepository registrationRepository,
            WaitlistService waitlistService,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.waitlistService = waitlistService;
        this.clock = clock;
    }

    /**
     * Registers a user for an event: CONFIRMED while seats remain, WAITING_LIST once it is full.
     * The event row stays locked until commit, so concurrent registrations never exceed capacity.
     */
    @Transactional
    public RegistrationResponse register(Long eventId, Long userId) {
        Event event = getEventForUpdate(eventId);
        User user = getUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        ensureAcceptsRegistrations(event, now);
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BusinessRuleViolationException("Inactive users cannot register for events");
        }
        Optional<Registration> existing = registrationRepository.findByEventIdAndUserId(eventId, userId);
        if (existing.isPresent() && ACTIVE_STATUSES.contains(existing.get().getStatus())) {
            throw new BusinessRuleViolationException("User is already registered for this event");
        }

        RegistrationStatus status = event.getRegisteredCount() < event.getCapacity()
                ? RegistrationStatus.CONFIRMED
                : RegistrationStatus.WAITING_LIST;
        if (status == RegistrationStatus.CONFIRMED) {
            event.setRegisteredCount(event.getRegisteredCount() + 1);
        }

        // (user_id, event_id) is unique, so a user who cancelled before reuses their registration.
        Registration registration = existing.orElseGet(() -> new Registration(user, event, now, status));
        registration.setRegistrationDate(now);
        registration.setStatus(status);
        return RegistrationResponse.from(registrationRepository.save(registration));
    }

    /**
     * Cancels the active registration of a user. A released confirmed seat goes to the oldest
     * waitlisted registration within the same locked transaction.
     */
    @Transactional
    public RegistrationCancellationResponse cancel(Long eventId, Long userId) {
        Event event = getEventForUpdate(eventId);
        ensureUserExists(userId);
        Registration registration = getRegistration(eventId, userId);

        if (CLOSED_STATUSES.contains(event.getStatus())) {
            throw new BusinessRuleViolationException(
                    "Registrations of an event with status " + event.getStatus() + " cannot be cancelled");
        }
        if (registration.getStatus() == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleViolationException("Registration is already cancelled");
        }

        boolean releasesSeat = registration.getStatus() == RegistrationStatus.CONFIRMED;
        registration.setStatus(RegistrationStatus.CANCELLED);
        RegistrationResponse promoted = null;
        if (releasesSeat) {
            event.setRegisteredCount(event.getRegisteredCount() - 1);
            promoted = waitlistService.fillFreeSeats(event).stream()
                    .findFirst()
                    .map(RegistrationResponse::from)
                    .orElse(null);
        }
        return new RegistrationCancellationResponse(RegistrationResponse.from(registration), promoted);
    }

    public RegistrationResponse findOne(Long eventId, Long userId) {
        ensureEventExists(eventId);
        ensureUserExists(userId);
        return RegistrationResponse.from(getRegistration(eventId, userId));
    }

    public List<RegistrationResponse> findByEvent(Long eventId, RegistrationStatus status) {
        ensureEventExists(eventId);
        List<Registration> registrations = status == null
                ? registrationRepository.findByEventIdOrderByRegistrationDateAscIdAsc(eventId)
                : registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(eventId, status);
        return registrations.stream()
                .map(RegistrationResponse::from)
                .toList();
    }

    public List<WaitlistEntryResponse> findWaitlist(Long eventId) {
        ensureEventExists(eventId);
        List<Registration> waitlist = registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                eventId, RegistrationStatus.WAITING_LIST);
        return IntStream.range(0, waitlist.size())
                .mapToObj(index -> WaitlistEntryResponse.from(index + 1, waitlist.get(index)))
                .toList();
    }

    public List<UserEventResponse> findEventsOfUser(Long userId, RegistrationStatus status) {
        ensureUserExists(userId);
        List<Registration> registrations = status == null
                ? registrationRepository.findByUserIdOrderByEventStartDateAscIdAsc(userId)
                : registrationRepository.findByUserIdAndStatusOrderByEventStartDateAscIdAsc(userId, status);
        return registrations.stream()
                .map(UserEventResponse::from)
                .toList();
    }

    private void ensureAcceptsRegistrations(Event event, LocalDateTime now) {
        if (CLOSED_STATUSES.contains(event.getStatus())) {
            throw new BusinessRuleViolationException(
                    "Event with status " + event.getStatus() + " does not accept registrations");
        }
        if (!now.isBefore(event.getStartDate())) {
            throw new BusinessRuleViolationException(
                    "Registrations are closed because the event has already started");
        }
    }

    private Event getEventForUpdate(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Registration getRegistration(Long eventId, Long userId) {
        return registrationRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new RegistrationNotFoundException(eventId, userId));
    }

    private void ensureEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }
}
