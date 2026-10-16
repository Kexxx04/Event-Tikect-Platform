package com.eventplatform.api.dto.auth;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        String username
) {
}
