package com.eventplatform.api.dto.registration;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.time.LocalDateTime;

public record UserEventResponse(
        Long registrationId,
        Long eventId,
        String eventName,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventStatus eventStatus,
        LocalDateTime registrationDate,
        RegistrationStatus registrationStatus
) {
    public static UserEventResponse from(Registration registration) {
        Event event = registration.getEvent();
        return new UserEventResponse(
                registration.getId(),
                event.getId(),
                event.getName(),
                event.getStartDate(),
                event.getEndDate(),
                event.getStatus(),
                registration.getRegistrationDate(),
                registration.getStatus()
        );
    }
}
