package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 1, 12, 0);
    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 10, 18, 0);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private WaitlistService waitlistService;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        registrationService = new RegistrationService(
                eventRepository, userRepository, registrationRepository, waitlistService, clock);
    }

    private Event lockedEvent(EventStatus status, LocalDateTime startDate, int capacity, int registeredCount) {
        Event event = new Event("Java Conf", null, startDate, startDate.plusHours(3), capacity, status);
        event.setId(1L);
        event.setRegisteredCount(registeredCount);
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        return event;
    }

    private Event lockedEvent(int capacity, int registeredCount) {
        return lockedEvent(EventStatus.SCHEDULED, START, capacity, registeredCount);
    }

    private User user(long id, UserStatus status) {
        User user = new User("User " + id, id + "@example.com", "D" + id);
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private User storedUser(UserStatus status) {
        User user = user(2L, status);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        return user;
    }

    private Registration registration(Event event, User user, RegistrationStatus status) {
        Registration registration = new Registration(user, event, NOW.minusDays(1), status);
        registration.setId(10L + user.getId());
        return registration;
    }

    private void returnSavedRegistration() {
        when(registrationRepository.save(any(Registration.class))).thenAnswer(invocation -> {
            Registration saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(100L);
            }
            return saved;
        });
    }

    // --- register ---

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "ACTIVE"})
    void confirmsTheRegistrationWhileSeatsRemain(EventStatus status) {
        Event event = lockedEvent(status, START, 10, 9);
        storedUser(UserStatus.ACTIVE);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        returnSavedRegistration();

        RegistrationResponse response = registrationService.register(1L, 2L);

        assertEquals(new RegistrationResponse(100L, 1L, "Java Conf", 2L, "User 2", "2@example.com",
                NOW, RegistrationStatus.CONFIRMED), response);
        assertEquals(10, event.getRegisteredCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11})
    void waitlistsTheRegistrationWhenTheEventIsFull(int registeredCount) {
        Event event = lockedEvent(10, registeredCount);
        storedUser(UserStatus.ACTIVE);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        returnSavedRegistration();

        RegistrationResponse response = registrationService.register(1L, 2L);

        assertEquals(RegistrationStatus.WAITING_LIST, response.status());
        assertEquals(NOW, response.registrationDate());
        assertEquals(registeredCount, event.getRegisteredCount());
    }

    @Test
    void reusesTheCancelledRegistrationOfAReturningUser() {
        Event event = lockedEvent(10, 3);
        User user = storedUser(UserStatus.ACTIVE);
        Registration cancelled = registration(event, user, RegistrationStatus.CANCELLED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(cancelled));
        returnSavedRegistration();

        RegistrationResponse response = registrationService.register(1L, 2L);

        assertEquals(12L, response.id());
        assertEquals(RegistrationStatus.CONFIRMED, cancelled.getStatus());
        assertEquals(NOW, cancelled.getRegistrationDate());
        assertEquals(4, event.getRegisteredCount());
    }

    @Test
    void sendsAReturningUserToTheEndOfTheWaitlist() {
        Event event = lockedEvent(10, 10);
        User user = storedUser(UserStatus.ACTIVE);
        Registration cancelled = registration(event, user, RegistrationStatus.CANCELLED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(cancelled));
        returnSavedRegistration();

        registrationService.register(1L, 2L);

        assertEquals(RegistrationStatus.WAITING_LIST, cancelled.getStatus());
        assertEquals(NOW, cancelled.getRegistrationDate());
        assertEquals(10, event.getRegisteredCount());
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"CONFIRMED", "WAITING_LIST"})
    void rejectsADuplicateActiveRegistration(RegistrationStatus status) {
        Event event = lockedEvent(10, 3);
        User user = storedUser(UserStatus.ACTIVE);
        Registration existing = registration(event, user, status);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.register(1L, 2L));

        assertEquals("User is already registered for this event", exception.getMessage());
        assertEquals(status, existing.getStatus());
        assertEquals(3, event.getRegisteredCount());
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    void rejectsInactiveUsers() {
        Event event = lockedEvent(10, 3);
        storedUser(UserStatus.INACTIVE);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.register(1L, 2L));

        assertEquals("Inactive users cannot register for events", exception.getMessage());
        assertEquals(3, event.getRegisteredCount());
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"FINISHED", "CANCELLED"})
    void rejectsRegistrationsForClosedEvents(EventStatus status) {
        Event event = lockedEvent(status, START, 10, 3);
        storedUser(UserStatus.ACTIVE);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.register(1L, 2L));

        assertEquals("Event with status " + status + " does not accept registrations", exception.getMessage());
        assertEquals(3, event.getRegisteredCount());
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 600})
    void rejectsRegistrationsOnceTheEventHasStarted(long minutesSinceStart) {
        Event event = lockedEvent(EventStatus.ACTIVE, NOW.minusMinutes(minutesSinceStart), 10, 3);
        storedUser(UserStatus.ACTIVE);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.register(1L, 2L));

        assertEquals("Registrations are closed because the event has already started", exception.getMessage());
        assertEquals(3, event.getRegisteredCount());
        verify(registrationRepository, never()).save(any(Registration.class));
    }

    @Test
    void acceptsRegistrationsUntilTheMomentBeforeStart() {
        lockedEvent(EventStatus.SCHEDULED, NOW.plusSeconds(1), 10, 3);
        storedUser(UserStatus.ACTIVE);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        returnSavedRegistration();

        assertEquals(RegistrationStatus.CONFIRMED, registrationService.register(1L, 2L).status());
    }

    @Test
    void rejectsRegistrationsForMissingEventsOrUsers() {
        when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        EventNotFoundException missingEvent =
                assertThrows(EventNotFoundException.class, () -> registrationService.register(99L, 2L));
        assertEquals("Event with id 99 was not found", missingEvent.getMessage());

        lockedEvent(10, 3);
        when(userRepository.findById(98L)).thenReturn(Optional.empty());
        UserNotFoundException missingUser =
                assertThrows(UserNotFoundException.class, () -> registrationService.register(1L, 98L));
        assertEquals("User with id 98 was not found", missingUser.getMessage());
        verifyNoInteractions(registrationRepository);
    }

    // --- cancel ---

    @Test
    void cancellingAConfirmedRegistrationPromotesTheFirstWaitlistedUser() {
        Event event = lockedEvent(10, 10);
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration confirmed = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        Registration next = registration(event, user(3L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(confirmed));
        when(waitlistService.fillFreeSeats(event)).thenAnswer(invocation -> {
            assertEquals(9, event.getRegisteredCount());
            event.setRegisteredCount(10);
            return List.of(next);
        });

        RegistrationCancellationResponse response = registrationService.cancel(1L, 2L);

        assertEquals(RegistrationStatus.CANCELLED, confirmed.getStatus());
        assertEquals(RegistrationResponse.from(confirmed), response.cancelled());
        assertEquals(RegistrationResponse.from(next), response.promoted());
        assertEquals(10, event.getRegisteredCount());
    }

    @Test
    void cancellingAConfirmedRegistrationWithoutWaitlistReleasesTheSeat() {
        Event event = lockedEvent(10, 4);
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration confirmed = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(confirmed));
        when(waitlistService.fillFreeSeats(event)).thenReturn(List.of());

        RegistrationCancellationResponse response = registrationService.cancel(1L, 2L);

        assertEquals(RegistrationStatus.CANCELLED, response.cancelled().status());
        assertNull(response.promoted());
        assertEquals(3, event.getRegisteredCount());
    }

    @Test
    void cancellingAWaitlistedRegistrationKeepsTheConfirmedSeats() {
        Event event = lockedEvent(10, 10);
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration waitlisted = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.WAITING_LIST);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(waitlisted));

        RegistrationCancellationResponse response = registrationService.cancel(1L, 2L);

        assertEquals(RegistrationStatus.CANCELLED, waitlisted.getStatus());
        assertNull(response.promoted());
        assertEquals(10, event.getRegisteredCount());
        verifyNoInteractions(waitlistService);
    }

    @Test
    void rejectsCancellingAnAlreadyCancelledRegistration() {
        Event event = lockedEvent(10, 4);
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration cancelled = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CANCELLED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(cancelled));

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.cancel(1L, 2L));

        assertEquals("Registration is already cancelled", exception.getMessage());
        assertEquals(4, event.getRegisteredCount());
        verifyNoInteractions(waitlistService);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"FINISHED", "CANCELLED"})
    void rejectsCancellingRegistrationsOfClosedEvents(EventStatus status) {
        Event event = lockedEvent(status, START, 10, 4);
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration confirmed = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(confirmed));

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> registrationService.cancel(1L, 2L));

        assertEquals("Registrations of an event with status " + status + " cannot be cancelled",
                exception.getMessage());
        assertEquals(RegistrationStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(4, event.getRegisteredCount());
    }

    @Test
    void rejectsCancellingMissingRegistrations() {
        lockedEvent(10, 4);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        RegistrationNotFoundException exception =
                assertThrows(RegistrationNotFoundException.class, () -> registrationService.cancel(1L, 2L));

        assertEquals("Registration of user 2 for event 1 was not found", exception.getMessage());
    }

    @Test
    void rejectsCancellingForMissingEventsOrUsers() {
        when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(EventNotFoundException.class, () -> registrationService.cancel(99L, 2L));

        lockedEvent(10, 4);
        when(userRepository.existsById(98L)).thenReturn(false);
        assertThrows(UserNotFoundException.class, () -> registrationService.cancel(1L, 98L));
        verifyNoInteractions(registrationRepository, waitlistService);
    }

    // --- queries ---

    private Event event() {
        Event event = new Event("Java Conf", null, START, START.plusHours(3), 10, EventStatus.SCHEDULED);
        event.setId(1L);
        return event;
    }

    private Event storedEvent() {
        when(eventRepository.existsById(1L)).thenReturn(true);
        return event();
    }

    @Test
    void retrievesTheRegistrationOfAUserForAnEvent() {
        Event event = storedEvent();
        when(userRepository.existsById(2L)).thenReturn(true);
        Registration registration = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CANCELLED);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.of(registration));

        assertEquals(RegistrationResponse.from(registration), registrationService.findOne(1L, 2L));
    }

    @Test
    void rejectsRetrievingUnknownRegistrations() {
        when(eventRepository.existsById(99L)).thenReturn(false);
        assertThrows(EventNotFoundException.class, () -> registrationService.findOne(99L, 2L));

        storedEvent();
        when(userRepository.existsById(98L)).thenReturn(false);
        assertThrows(UserNotFoundException.class, () -> registrationService.findOne(1L, 98L));

        when(userRepository.existsById(2L)).thenReturn(true);
        when(registrationRepository.findByEventIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        assertThrows(RegistrationNotFoundException.class, () -> registrationService.findOne(1L, 2L));
    }

    @Test
    void listsAllRegistrationsOfAnEvent() {
        Event event = storedEvent();
        Registration confirmed = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        Registration waitlisted = registration(event, user(3L, UserStatus.ACTIVE), RegistrationStatus.WAITING_LIST);
        when(registrationRepository.findByEventIdOrderByRegistrationDateAscIdAsc(1L))
                .thenReturn(List.of(confirmed, waitlisted));

        assertEquals(List.of(RegistrationResponse.from(confirmed), RegistrationResponse.from(waitlisted)),
                registrationService.findByEvent(1L, null));
    }

    @Test
    void listsTheRegistrationsOfAnEventWithAStatus() {
        Event event = storedEvent();
        Registration confirmed = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.CONFIRMED);
        when(registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                1L, RegistrationStatus.CONFIRMED)).thenReturn(List.of(confirmed));

        assertEquals(List.of(RegistrationResponse.from(confirmed)),
                registrationService.findByEvent(1L, RegistrationStatus.CONFIRMED));
    }

    @Test
    void numbersTheWaitlistInPromotionOrder() {
        Event event = storedEvent();
        Registration first = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.WAITING_LIST);
        Registration second = registration(event, user(3L, UserStatus.ACTIVE), RegistrationStatus.WAITING_LIST);
        when(registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                1L, RegistrationStatus.WAITING_LIST)).thenReturn(List.of(first, second));

        List<WaitlistEntryResponse> waitlist = registrationService.findWaitlist(1L);

        assertEquals(List.of(WaitlistEntryResponse.from(1, first), WaitlistEntryResponse.from(2, second)), waitlist);
        assertEquals(3L, waitlist.get(1).userId());
    }

    @Test
    void rejectsListingRegistrationsOfMissingEvents() {
        when(eventRepository.existsById(99L)).thenReturn(false);
        assertThrows(EventNotFoundException.class, () -> registrationService.findByEvent(99L, null));
        assertThrows(EventNotFoundException.class, () -> registrationService.findWaitlist(99L));
        verifyNoInteractions(registrationRepository);
    }

    @Test
    void listsTheEventsOfAUser() {
        Event event = event();
        User user = user(2L, UserStatus.ACTIVE);
        Registration registration = registration(event, user, RegistrationStatus.CONFIRMED);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(registrationRepository.findByUserIdOrderByEventStartDateAscIdAsc(2L)).thenReturn(List.of(registration));

        List<UserEventResponse> events = registrationService.findEventsOfUser(2L, null);

        assertEquals(List.of(new UserEventResponse(12L, 1L, "Java Conf", START, START.plusHours(3),
                EventStatus.SCHEDULED, NOW.minusDays(1), RegistrationStatus.CONFIRMED)), events);
    }

    @Test
    void listsTheEventsOfAUserWithARegistrationStatus() {
        Event event = event();
        Registration registration = registration(event, user(2L, UserStatus.ACTIVE), RegistrationStatus.WAITING_LIST);
        when(userRepository.existsById(2L)).thenReturn(true);
        when(registrationRepository.findByUserIdAndStatusOrderByEventStartDateAscIdAsc(
                2L, RegistrationStatus.WAITING_LIST)).thenReturn(List.of(registration));

        List<UserEventResponse> events = registrationService.findEventsOfUser(2L, RegistrationStatus.WAITING_LIST);

        assertEquals(1, events.size());
        assertSame(RegistrationStatus.WAITING_LIST, events.get(0).registrationStatus());
    }

    @Test
    void rejectsListingTheEventsOfMissingUsers() {
        when(userRepository.existsById(98L)).thenReturn(false);
        UserNotFoundException exception =
                assertThrows(UserNotFoundException.class, () -> registrationService.findEventsOfUser(98L, null));
        assertTrue(exception.getMessage().contains("98"));
        verifyNoInteractions(registrationRepository);
    }
}
