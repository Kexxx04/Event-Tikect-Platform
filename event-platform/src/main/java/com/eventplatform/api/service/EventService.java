package com.eventplatform.api.service;

import com.eventplatform.api.dto.event.CreateEventRequest;
import com.eventplatform.api.dto.event.EventAvailabilityResponse;
import com.eventplatform.api.dto.event.EventResponse;
import com.eventplatform.api.dto.event.UpdateEventRequest;
import com.eventplatform.api.exception.BusinessRuleViolationException;
import com.eventplatform.api.exception.EventNotFoundException;
import com.eventplatform.api.exception.InvalidEventDataException;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.RegistrationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Map<EventStatus, Set<EventStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(Map.of(
            EventStatus.SCHEDULED, EnumSet.of(EventStatus.ACTIVE, EventStatus.CANCELLED),
            EventStatus.ACTIVE, EnumSet.of(EventStatus.FINISHED, EventStatus.CANCELLED),
            EventStatus.FINISHED, EnumSet.noneOf(EventStatus.class),
            EventStatus.CANCELLED, EnumSet.noneOf(EventStatus.class)
    ));

    private static final Set<EventStatus> OPEN_STATUSES = EnumSet.of(EventStatus.SCHEDULED, EventStatus.ACTIVE);

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final Clock clock;

    public EventService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.clock = clock;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        String name = normalizeName(request.name());
        validateDates(request.startDate(), request.endDate());
        validateCapacity(request.capacity());

        Event event = new Event(
                name,
                normalizeDescription(request.description()),
                request.startDate(),
                request.endDate(),
                request.capacity(),
                EventStatus.SCHEDULED
        );
        return EventResponse.from(eventRepository.save(event));
    }

    public List<EventResponse> findAll() {
        return eventRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    public EventResponse findById(Long id) {
        return EventResponse.from(getEvent(id));
    }

    public EventAvailabilityResponse getAvailability(Long id) {
        Event event = getEvent(id);
        long waitlistCount = registrationRepository.countByEventIdAndStatus(id, RegistrationStatus.WAITING_LIST);
        return EventAvailabilityResponse.from(event, waitlistCount, isAcceptingRegistrations(event));
    }

    @Transactional
    public EventResponse update(Long id, UpdateEventRequest request) {
        Event event = getEventForUpdate(id);
        ensureModifiable(event);
        String name = normalizeName(request.name());
        validateDates(request.startDate(), request.endDate());
        validateCapacity(request.capacity());
        if (request.capacity() < event.getRegisteredCount()) {
            throw new BusinessRuleViolationException(
                    "Capacity cannot be lower than the registered count (" + event.getRegisteredCount() + ")");
        }

        event.setName(name);
        event.setDescription(normalizeDescription(request.description()));
        event.setStartDate(request.startDate());
        event.setEndDate(request.endDate());
        event.setCapacity(request.capacity());
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateStatus(Long id, EventStatus status) {
        Event event = getEventForUpdate(id);
        if (!ALLOWED_TRANSITIONS.get(event.getStatus()).contains(status)) {
            throw new BusinessRuleViolationException(
                    "Cannot change event status from " + event.getStatus() + " to " + status);
        }
        event.setStatus(status);
        return EventResponse.from(event);
    }

    private Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    private boolean isAcceptingRegistrations(Event event) {
        return OPEN_STATUSES.contains(event.getStatus())
                && LocalDateTime.now(clock).isBefore(event.getStartDate());
    }

    private Event getEventForUpdate(Long id) {
        return eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    private void ensureModifiable(Event event) {
        if (event.getStatus() == EventStatus.FINISHED || event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException(
                    "Event with status " + event.getStatus() + " cannot be modified");
        }
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidEventDataException("Start date and end date are required");
        }
        if (!endDate.isAfter(startDate)) {
            throw new InvalidEventDataException("End date must be after start date");
        }
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw new InvalidEventDataException("Capacity must be greater than zero");
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new InvalidEventDataException("Name must not be blank");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
