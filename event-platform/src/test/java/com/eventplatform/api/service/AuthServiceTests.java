package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.eventplatform.api.config.SecurityProperties;
import com.eventplatform.api.dto.auth.LoginRequest;
import com.eventplatform.api.dto.auth.LoginResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    private static final Instant NOW = Instant.parse("2030-05-01T12:00:00Z");
    private static final Duration EXPIRATION = Duration.ofHours(8);
    private static final SecretKey KEY = new SecretKeySpec(
            "unit-test-secret-with-at-least-32-characters".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Admin("admin", "secret"),
                new SecurityProperties.Jwt("unused-here-because-the-key-is-built-in-the-test", EXPIRATION));
        authService = new AuthService(
                authenticationManager,
                new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);
        decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Test
    void issuesASignedTokenForValidCredentials() {
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        ArgumentCaptor<Authentication> attempt = ArgumentCaptor.forClass(Authentication.class);
        when(authenticationManager.authenticate(attempt.capture())).thenReturn(authenticated);

        LoginResponse response = authService.login(new LoginRequest("admin", "secret"));

        assertEquals("admin", attempt.getValue().getName());
        assertEquals("secret", attempt.getValue().getCredentials());
        assertEquals("Bearer", response.tokenType());
        assertEquals("admin", response.username());
        assertEquals(NOW.plus(EXPIRATION), response.expiresAt());

        Jwt jwt = decoder.decode(response.token());
        assertEquals("admin", jwt.getSubject());
        assertEquals("event-platform", jwt.getClaimAsString("iss"));
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plus(EXPIRATION), jwt.getExpiresAt());
    }

    @Test
    void propagatesWrongCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        LoginRequest request = new LoginRequest("admin", "wrong");

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
