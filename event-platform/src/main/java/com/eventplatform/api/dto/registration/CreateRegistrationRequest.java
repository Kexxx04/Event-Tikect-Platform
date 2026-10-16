package com.eventplatform.api.dto.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRegistrationRequest(
        @NotNull(message = "User id is required")
        @Positive(message = "User id must be greater than zero")
        Long userId
) {
}
