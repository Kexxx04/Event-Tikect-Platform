package com.eventplatform.api.dto.event;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.enums.EventStatus;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer capacity,
        Integer registeredCount,
        EventStatus status
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getDescription(),
                event.getStartDate(),
                event.getEndDate(),
                event.getCapacity(),
                event.getRegisteredCount(),
                event.getStatus()
        );
    }
}
