package com.eventplatform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eventplatform.api.dto.registration.RegistrationResponse;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.UserRepository;
import com.eventplatform.api.service.RegistrationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Stress scenario of the specification against the real schema: 1000 users race for 100 seats
 * and PostgreSQL row locks must serialize them (100 CONFIRMED, 900 WAITING_LIST, 0 overbooked).
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.maximum-pool-size=20",
        // Test-only credentials and signing key.
        "app.security.admin.username=admin",
        "app.security.admin.password=postgres-test-password",
        "app.security.jwt.secret=postgres-test-jwt-secret-with-32-plus-chars",
        "app.security.jwt.expiration=PT1H"
})
class PostgresRegistrationConcurrencyTests {

    private static final int CAPACITY = 100;
    private static final int USERS = 1000;

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // Always use the disposable container, never DB_URL or the developer's database.
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    RegistrationService registrationService;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void oneThousandUsersRaceForOneHundredSeatsWithoutOverbooking() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 5, 10, 18, 0);
        Long eventId = eventRepository.save(
                new Event("Stress Conf", null, start, start.plusHours(3), CAPACITY, EventStatus.SCHEDULED)).getId();
        String batch = UUID.randomUUID().toString();
        List<Long> userIds = userRepository.saveAll(IntStream.range(0, USERS)
                        .mapToObj(index -> new User("Stress " + index, batch + "-" + index + "@example.com",
                                batch + "-" + index))
                        .toList())
                .stream()
                .map(User::getId)
                .toList();

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(64);
        List<RegistrationResponse> responses = new ArrayList<>();
        try {
            List<Future<RegistrationResponse>> futures = new ArrayList<>();
            for (Long userId : userIds) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return registrationService.register(eventId, userId);
                }));
            }
            startGate.countDown();
            for (Future<RegistrationResponse> future : futures) {
                responses.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        Map<RegistrationStatus, Long> byStatus = responses.stream()
                .collect(Collectors.groupingBy(RegistrationResponse::status, Collectors.counting()));
        assertEquals(CAPACITY, byStatus.get(RegistrationStatus.CONFIRMED));
        assertEquals(USERS - CAPACITY, byStatus.get(RegistrationStatus.WAITING_LIST));
        assertEquals(CAPACITY, jdbc.queryForObject(
                "SELECT registered_count FROM events WHERE id = ?", Integer.class, eventId));
        assertEquals(CAPACITY, jdbc.queryForObject(
                "SELECT count(*) FROM registrations WHERE event_id = ? AND status = 'CONFIRMED'",
                Integer.class, eventId));
        assertEquals(USERS - CAPACITY, jdbc.queryForObject(
                "SELECT count(*) FROM registrations WHERE event_id = ? AND status = 'WAITING_LIST'",
                Integer.class, eventId));
    }
}
