package com.eventplatform.api.dto.event;

import com.eventplatform.api.model.enums.EventStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEventStatusRequest(
        @NotNull(message = "Status is required")
        EventStatus status
) {
}
