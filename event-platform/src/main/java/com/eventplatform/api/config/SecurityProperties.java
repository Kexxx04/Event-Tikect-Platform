package com.eventplatform.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Admin admin,
        @Valid @NotNull Jwt jwt
) {

    public record Admin(
            @NotBlank(message = "Set ADMIN_USERNAME (app.security.admin.username)")
            String username,

            @NotBlank(message = "Set ADMIN_PASSWORD (app.security.admin.password)")
            String password
    ) {
    }

    public record Jwt(
            // HS256 needs a key of at least 256 bits.
            @NotNull(message = "Set JWT_SECRET (app.security.jwt.secret)")
            @Size(min = 32, message = "JWT_SECRET must contain at least 32 characters")
            String secret,

            @NotNull(message = "Set JWT_EXPIRATION (app.security.jwt.expiration)")
            Duration expiration
    ) {
    }
}
