package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventplatform.api.dto.event.CreateEventRequest;
import com.eventplatform.api.dto.event.EventResponse;
import com.eventplatform.api.dto.event.UpdateEventRequest;
import com.eventplatform.api.exception.BusinessRuleViolationException;
import com.eventplatform.api.exception.EventNotFoundException;
import com.eventplatform.api.exception.InvalidEventDataException;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class EventServiceTests {

    private static final LocalDateTime START = LocalDateTime.of(2030, 5, 10, 18, 0);
    private static final LocalDateTime END = START.plusHours(3);

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private Event event(EventStatus status) {
        Event event = new Event("Java Conf", "Talks", START, END, 50, status);
        event.setId(1L);
        return event;
    }

    private Event lockedEvent(EventStatus status) {
        Event event = event(status);
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        return event;
    }

    private UpdateEventRequest validUpdate(int capacity) {
        return new UpdateEventRequest("Spring Conf", "Workshops", START.plusDays(1), END.plusDays(1), capacity);
    }

    @Test
    void createsAScheduledEventWithNormalizedData() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        EventResponse response = eventService.create(
                new CreateEventRequest("  Java Conf  ", "  Talks  ", START, END, 50));

        assertEquals(1L, response.id());
        assertEquals("Java Conf", response.name());
        assertEquals("Talks", response.description());
        assertEquals(START, response.startDate());
        assertEquals(END, response.endDate());
        assertEquals(50, response.capacity());
        assertEquals(0, response.registeredCount());
        assertEquals(EventStatus.SCHEDULED, response.status());
    }

    @Test
    void storesBlankDescriptionAsNull() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertNull(eventService.create(new CreateEventRequest("Java Conf", "   ", START, END, 1)).description());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingName(String invalid) {
        CreateEventRequest request = new CreateEventRequest(invalid, null, START, END, 50);
        assertThrows(InvalidEventDataException.class, () -> eventService.create(request));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void rejectsMissingDates() {
        CreateEventRequest missingStart = new CreateEventRequest("Java Conf", null, null, END, 50);
        CreateEventRequest missingEnd = new CreateEventRequest("Java Conf", null, START, null, 50);
        assertThrows(InvalidEventDataException.class, () -> eventService.create(missingStart));
        assertThrows(InvalidEventDataException.class, () -> eventService.create(missingEnd));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -60})
    void rejectsEndDateNotAfterStartDate(long minutesAfterStart) {
        CreateEventRequest request = new CreateEventRequest(
                "Java Conf", null, START, START.plusMinutes(minutesAfterStart), 50);
        InvalidEventDataException exception =
                assertThrows(InvalidEventDataException.class, () -> eventService.create(request));
        assertEquals("End date must be after start date", exception.getMessage());
        verify(eventRepository, never()).save(any(Event.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(Integer capacity) {
        CreateEventRequest request = new CreateEventRequest("Java Conf", null, START, END, capacity);
        assertThrows(InvalidEventDataException.class, () -> eventService.create(request));
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void listsEventsInIdOrder() {
        Event event = event(EventStatus.SCHEDULED);
        when(eventRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))).thenReturn(List.of(event));
        assertEquals(List.of(EventResponse.from(event)), eventService.findAll());
    }

    @Test
    void retrievesAnEventById() {
        Event event = event(EventStatus.SCHEDULED);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        assertEquals(EventResponse.from(event), eventService.findById(1L));
    }

    @Test
    void rejectsMissingEventsForReadUpdateAndStatus() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());
        when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        UpdateEventRequest request = validUpdate(10);

        assertThrows(EventNotFoundException.class, () -> eventService.findById(99L));
        assertThrows(EventNotFoundException.class, () -> eventService.update(99L, request));
        EventNotFoundException exception = assertThrows(EventNotFoundException.class,
                () -> eventService.updateStatus(99L, EventStatus.ACTIVE));
        assertEquals("Event with id 99 was not found", exception.getMessage());
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"SCHEDULED", "ACTIVE"})
    void replacesAllEditableFields(EventStatus status) {
        Event event = lockedEvent(status);
        event.setRegisteredCount(5);

        EventResponse response = eventService.update(1L, validUpdate(80));

        assertEquals("Spring Conf", event.getName());
        assertEquals("Workshops", event.getDescription());
        assertEquals(START.plusDays(1), event.getStartDate());
        assertEquals(END.plusDays(1), event.getEndDate());
        assertEquals(80, event.getCapacity());
        assertEquals(5, response.registeredCount());
        assertEquals(status, response.status());
    }

    @Test
    void replacesDescriptionWithNullWhenOmitted() {
        Event event = lockedEvent(EventStatus.SCHEDULED);
        eventService.update(1L, new UpdateEventRequest("Java Conf", null, START, END, 50));
        assertNull(event.getDescription());
    }

    @Test
    void allowsCapacityEqualToRegisteredCount() {
        Event event = lockedEvent(EventStatus.ACTIVE);
        event.setRegisteredCount(20);
        assertEquals(20, eventService.update(1L, validUpdate(20)).capacity());
    }

    @Test
    void rejectsCapacityBelowRegisteredCountWithoutChangingEvent() {
        Event event = lockedEvent(EventStatus.ACTIVE);
        event.setRegisteredCount(20);
        EventResponse before = EventResponse.from(event);
        UpdateEventRequest request = validUpdate(19);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> eventService.update(1L, request));

        assertEquals("Capacity cannot be lower than the registered count (20)", exception.getMessage());
        assertEquals(before, EventResponse.from(event));
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"FINISHED", "CANCELLED"})
    void rejectsUpdatingClosedEvents(EventStatus status) {
        Event event = lockedEvent(status);
        EventResponse before = EventResponse.from(event);
        UpdateEventRequest request = validUpdate(80);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> eventService.update(1L, request));

        assertEquals("Event with status " + status + " cannot be modified", exception.getMessage());
        assertEquals(before, EventResponse.from(event));
    }

    @Test
    void rejectsInvalidUpdateDataWithoutChangingEvent() {
        Event event = lockedEvent(EventStatus.SCHEDULED);
        EventResponse before = EventResponse.from(event);
        UpdateEventRequest badDates = new UpdateEventRequest("Java Conf", null, END, START, 50);
        UpdateEventRequest badName = new UpdateEventRequest("  ", null, START, END, 50);
        UpdateEventRequest badCapacity = new UpdateEventRequest("Java Conf", null, START, END, 0);

        assertThrows(InvalidEventDataException.class, () -> eventService.update(1L, badDates));
        assertThrows(InvalidEventDataException.class, () -> eventService.update(1L, badName));
        assertThrows(InvalidEventDataException.class, () -> eventService.update(1L, badCapacity));
        assertEquals(before, EventResponse.from(event));
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, ACTIVE",
            "SCHEDULED, CANCELLED",
            "ACTIVE, FINISHED",
            "ACTIVE, CANCELLED"
    })
    void appliesValidStatusTransitions(EventStatus from, EventStatus to) {
        Event event = lockedEvent(from);
        assertEquals(to, eventService.updateStatus(1L, to).status());
        assertEquals(to, event.getStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED, SCHEDULED",
            "SCHEDULED, FINISHED",
            "ACTIVE, ACTIVE",
            "ACTIVE, SCHEDULED",
            "FINISHED, SCHEDULED",
            "FINISHED, ACTIVE",
            "FINISHED, FINISHED",
            "FINISHED, CANCELLED",
            "CANCELLED, SCHEDULED",
            "CANCELLED, ACTIVE",
            "CANCELLED, FINISHED",
            "CANCELLED, CANCELLED"
    })
    void rejectsInvalidStatusTransitions(EventStatus from, EventStatus to) {
        Event event = lockedEvent(from);

        BusinessRuleViolationException exception =
                assertThrows(BusinessRuleViolationException.class, () -> eventService.updateStatus(1L, to));

        assertEquals("Cannot change event status from " + from + " to " + to, exception.getMessage());
        assertEquals(from, event.getStatus());
    }
}
