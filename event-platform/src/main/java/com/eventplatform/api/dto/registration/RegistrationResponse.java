package com.eventplatform.api.dto.registration;

import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.time.LocalDateTime;

public record RegistrationResponse(
        Long id,
        Long eventId,
        String eventName,
        Long userId,
        String userName,
        String userEmail,
        LocalDateTime registrationDate,
        RegistrationStatus status
) {
    public static RegistrationResponse from(Registration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getEvent().getId(),
                registration.getEvent().getName(),
                registration.getUser().getId(),
                registration.getUser().getName(),
                registration.getUser().getEmail(),
                registration.getRegistrationDate(),
                registration.getStatus()
        );
    }
}
