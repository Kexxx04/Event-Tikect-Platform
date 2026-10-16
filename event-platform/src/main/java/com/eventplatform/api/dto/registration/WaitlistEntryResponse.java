package com.eventplatform.api.dto.registration;

import com.eventplatform.api.model.Registration;
import java.time.LocalDateTime;

public record WaitlistEntryResponse(
        int position,
        Long registrationId,
        Long userId,
        String userName,
        String userEmail,
        LocalDateTime registrationDate
) {
    public static WaitlistEntryResponse from(int position, Registration registration) {
        return new WaitlistEntryResponse(
                position,
                registration.getId(),
                registration.getUser().getId(),
                registration.getUser().getName(),
                registration.getUser().getEmail(),
                registration.getRegistrationDate()
        );
    }
}
