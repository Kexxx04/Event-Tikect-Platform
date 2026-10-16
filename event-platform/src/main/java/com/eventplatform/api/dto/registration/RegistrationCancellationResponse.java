package com.eventplatform.api.dto.registration;

/**
 * Result of cancelling a registration: the cancelled registration and, when a confirmed seat
 * was released, the waitlisted registration that took it (null when nobody was waiting).
 */
public record RegistrationCancellationResponse(
        RegistrationResponse cancelled,
        RegistrationResponse promoted
) {
}
