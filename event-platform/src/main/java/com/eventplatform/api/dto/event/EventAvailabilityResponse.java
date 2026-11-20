package com.eventplatform.api.dto.event;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.enums.EventStatus;

public record EventAvailabilityResponse(
        Long eventId,
        Integer capacity,
        Integer registeredCount,
        int availableSeats,
        long waitlistCount,
        EventStatus status,
        boolean acceptingRegistrations
) {
    public static EventAvailabilityResponse from(Event event, long waitlistCount, boolean acceptingRegistrations) {
        return new EventAvailabilityResponse(
                event.getId(),
                event.getCapacity(),
                event.getRegisteredCount(),
                Math.max(0, event.getCapacity() - event.getRegisteredCount()),
                waitlistCount,
                event.getStatus(),
                acceptingRegistrations
        );
    }
}
