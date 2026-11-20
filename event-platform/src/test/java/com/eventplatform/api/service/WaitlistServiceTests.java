package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.RegistrationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTests {

    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 10, 18, 0);

    @Mock
    private RegistrationRepository registrationRepository;

    private WaitlistService waitlistService;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(registrationRepository);
    }

    private Event event(int capacity, int registeredCount) {
        Event event = new Event("Java Conf", null, START, START.plusHours(3), capacity, EventStatus.SCHEDULED);
        event.setId(1L);
        event.setRegisteredCount(registeredCount);
        return event;
    }

    private Registration waitlisted(Event event, long id) {
        Registration registration = new Registration(
                new User("User " + id, id + "@example.com", "D" + id), event, START.minusDays(1),
                RegistrationStatus.WAITING_LIST);
        registration.setId(id);
        return registration;
    }

    @Test
    void promotesTheOldestWaitlistedRegistrationIntoTheReleasedSeat() {
        Event event = event(10, 9);
        Registration oldest = waitlisted(event, 7L);
        when(registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                1L, RegistrationStatus.WAITING_LIST, Limit.of(1))).thenReturn(List.of(oldest));

        List<Registration> promoted = waitlistService.fillFreeSeats(event);

        assertEquals(List.of(oldest), promoted);
        assertEquals(RegistrationStatus.CONFIRMED, oldest.getStatus());
        assertEquals(10, event.getRegisteredCount());
    }

    @Test
    void fillsEveryFreeSeatWhenTheCapacityGrows() {
        Event event = event(10, 7);
        Registration first = waitlisted(event, 1L);
        Registration second = waitlisted(event, 2L);
        when(registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                1L, RegistrationStatus.WAITING_LIST, Limit.of(3))).thenReturn(List.of(first, second));

        List<Registration> promoted = waitlistService.fillFreeSeats(event);

        assertEquals(List.of(first, second), promoted);
        assertEquals(RegistrationStatus.CONFIRMED, first.getStatus());
        assertEquals(RegistrationStatus.CONFIRMED, second.getStatus());
        assertEquals(9, event.getRegisteredCount());
    }

    @Test
    void keepsTheCountWhenNobodyIsWaiting() {
        Event event = event(10, 9);
        when(registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                1L, RegistrationStatus.WAITING_LIST, Limit.of(1))).thenReturn(List.of());

        assertTrue(waitlistService.fillFreeSeats(event).isEmpty());
        assertEquals(9, event.getRegisteredCount());
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11})
    void doesNotQueryTheWaitlistOfAFullEvent(int registeredCount) {
        Event event = event(10, registeredCount);

        assertTrue(waitlistService.fillFreeSeats(event).isEmpty());
        assertEquals(registeredCount, event.getRegisteredCount());
        verifyNoInteractions(registrationRepository);
    }
}
