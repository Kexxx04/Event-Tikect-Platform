package com.eventplatform.api.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventplatform.api.exception.ApiAuthenticationEntryPoint;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the bean factory methods directly, so each one is verified independently of the Spring context.
 * {@code securityFilterChain} is the exception: it needs the HttpSecurity that only Spring Security creates.
 */
class SecurityConfigTests {

    private static final String SECRET = "unit-test-secret-with-at-least-32-characters";

    private final SecurityConfig config = new SecurityConfig();
    private final SecurityProperties properties = new SecurityProperties(
            new SecurityProperties.Admin("admin", "s3cret-password"),
            new SecurityProperties.Jwt(SECRET, Duration.ofHours(1)));

    // A new minimal context on every run, so securityFilterChain always executes. The @SpringBootTest classes
    // share cached contexts whose chain was built once, so they cannot detect a change in this method.
    private final WebApplicationContextRunner securityContext = new WebApplicationContextRunner()
            .withUserConfiguration(SpringSecurity.class, SecurityConfig.class, CorsConfig.class,
                    ApiAuthenticationEntryPoint.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues(
                    "app.security.admin.username=admin",
                    "app.security.admin.password=s3cret-password",
                    "app.security.jwt.secret=" + SECRET,
                    "app.security.jwt.expiration=PT1H",
                    "app.cors.allowed-origins=http://localhost:5173");

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class SpringSecurity {
    }

    private String sign(JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("admin").issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void installsTheStatelessJwtFilterChain() {
        securityContext.run(context -> {
            List<SecurityFilterChain> chains = context.getBean(FilterChainProxy.class).getFilterChains();
            assertEquals(1, chains.size());
            List<Class<?>> filters = chains.get(0).getFilters().stream().<Class<?>>map(Object::getClass).toList();

            assertTrue(filters.contains(BearerTokenAuthenticationFilter.class), "JWT bearer tokens");
            assertTrue(filters.contains(CorsFilter.class), "CORS for the frontend");
            assertFalse(filters.contains(CsrfFilter.class), "stateless API without CSRF");
        });
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
