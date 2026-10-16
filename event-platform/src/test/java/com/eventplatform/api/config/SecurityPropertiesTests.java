package com.eventplatform.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityPropertiesTests {

    private static final String VALID_SECRET = "a-secret-with-exactly-32-chars!!";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private Set<String> violations(String username, String password, String secret, Duration expiration) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Admin(username, password),
                new SecurityProperties.Jwt(secret, expiration));
        return validator.validate(properties).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsCompleteConfiguration() {
        assertEquals(32, VALID_SECRET.length());
        assertTrue(violations("admin", "password", VALID_SECRET, Duration.ofHours(8)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiresTheAdminPassword(String password) {
        assertEquals(Set.of("Set ADMIN_PASSWORD (app.security.admin.password)"),
                violations("admin", password, VALID_SECRET, Duration.ofHours(8)));
    }

    @Test
    void requiresTheAdminUsername() {
        assertEquals(Set.of("Set ADMIN_USERNAME (app.security.admin.username)"),
                violations(" ", "password", VALID_SECRET, Duration.ofHours(8)));
    }

    @Test
    void rejectsShortOrMissingJwtSecrets() {
        assertEquals(Set.of("JWT_SECRET must contain at least 32 characters"),
                violations("admin", "password", VALID_SECRET.substring(1), Duration.ofHours(8)));
        assertEquals(Set.of("Set JWT_SECRET (app.security.jwt.secret)"),
                violations("admin", "password", null, Duration.ofHours(8)));
    }

    @Test
    void requiresTheTokenExpiration() {
        assertEquals(Set.of("Set JWT_EXPIRATION (app.security.jwt.expiration)"),
                violations("admin", "password", VALID_SECRET, null));
    }
}
