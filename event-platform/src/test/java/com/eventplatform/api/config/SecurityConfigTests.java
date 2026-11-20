package com.eventplatform.api.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/** Calls the bean factory methods directly, so each one is verified independently of the Spring context. */
class SecurityConfigTests {

    private static final String SECRET = "unit-test-secret-with-at-least-32-characters";

    private final SecurityConfig config = new SecurityConfig();
    private final SecurityProperties properties = new SecurityProperties(
            new SecurityProperties.Admin("admin", "s3cret-password"),
            new SecurityProperties.Jwt(SECRET, Duration.ofHours(1)));

    private String sign(JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("admin").issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void storesTheAdminPasswordHashedAndWithTheAdminRole() {
        PasswordEncoder passwordEncoder = config.passwordEncoder();
        UserDetailsService users = config.userDetailsService(properties, passwordEncoder);

        UserDetails admin = users.loadUserByUsername("admin");

        assertTrue(admin.getPassword().startsWith("$2"), "BCrypt hash expected");
        assertTrue(passwordEncoder.matches("s3cret-password", admin.getPassword()));
        assertEquals(List.of("ROLE_ADMIN"), admin.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void authenticatesOnlyTheConfiguredCredentials() {
        PasswordEncoder passwordEncoder = config.passwordEncoder();
        AuthenticationManager manager = config.authenticationManager(
                config.userDetailsService(properties, passwordEncoder), passwordEncoder);

        Authentication result = manager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "s3cret-password"));
        UsernamePasswordAuthenticationToken wrongPassword =
                UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong");

        assertTrue(result.isAuthenticated());
        assertEquals("admin", result.getName());
        assertThrows(BadCredentialsException.class, () -> manager.authenticate(wrongPassword));
    }

    @Test
    void buildsAnHmacSha256KeyFromTheConfiguredSecret() {
        SecretKey key = config.jwtSecretKey(properties);

        assertEquals("HmacSHA256", key.getAlgorithm());
        assertArrayEquals(SECRET.getBytes(StandardCharsets.UTF_8), key.getEncoded());
    }

    @Test
    void decodesTokensSignedWithTheSameKeyOnly() {
        SecretKey key = config.jwtSecretKey(properties);
        JwtDecoder decoder = config.jwtDecoder(key);
        SecretKey otherKey = new SecretKeySpec(
                "a-completely-different-secret-of-32-plus".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = sign(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)));

        assertEquals("admin", decoder.decode(sign(config.jwtEncoder(key))).getSubject());
        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    @Test
    void appliesCorsRulesOnlyToTheApi() {
        CorsConfigurationSource source = new CorsConfig(List.of("http://localhost:5173")).corsConfigurationSource();

        CorsConfiguration api = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/events"));

        assertEquals(List.of("http://localhost:5173"), api.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE"), api.getAllowedMethods());
        assertEquals(List.of("Content-Type", "Authorization"), api.getAllowedHeaders());
        assertEquals(null, source.getCorsConfiguration(new MockHttpServletRequest("GET", "/actuator/health")));
    }
}
