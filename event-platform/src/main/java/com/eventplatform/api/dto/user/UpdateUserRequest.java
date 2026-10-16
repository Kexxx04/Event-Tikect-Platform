package com.eventplatform.api.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 1, max = 255, message = "Name must contain between 1 and 255 characters")
        String name,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(min = 1, max = 255, message = "Document must contain between 1 and 255 characters")
        String document
) {
}
