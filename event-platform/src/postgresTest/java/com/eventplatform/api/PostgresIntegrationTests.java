package com.eventplatform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PostgresIntegrationTests {
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
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    @Test
    void appliesTheRealMigrationAndCreatesThePostgresIndex() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN ('users', 'events', 'registrations')
                """, Integer.class));
        String index = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND indexname = 'idx_registrations_waiting_list'
                """, String.class);
        assertTrue(index.contains("WHERE"));
        assertTrue(index.contains("WAITING_LIST"));
    }

    @Test
    void startsTheHttpServerAndPersistsAUser() throws Exception {
        String unique = UUID.randomUUID().toString();
        URI base = URI.create("http://localhost:" + port);
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/users"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"name":"Postgres Test", "email":"%s@example.com", "document":"%s"}
                            """.formatted(unique, unique)))
                    .build();
            var created = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, created.statusCode(), created.body());
            URI location = base.resolve(created.headers().firstValue("Location").orElseThrow());
            var retrieved = client.send(HttpRequest.newBuilder(location).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, retrieved.statusCode());
            assertEquals(created.body(), retrieved.body());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE document = ?", Integer.class, unique));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "('2030-01-01 10:00', '2030-01-01 10:00', 10, 0)",
            "('2030-01-01 11:00', '2030-01-01 10:00', 10, 0)",
            "('2030-01-01 10:00', '2030-01-01 11:00', 10, -1)",
            "('2030-01-01 10:00', '2030-01-01 11:00', 10, 11)",
            "('2030-01-01 10:00', '2030-01-01 11:00', 0, 0)"
    })
    void rejectsInvalidEventDataAtDatabaseLevel(String values) {
        // Values are fixed test literals, not external input.
        String sql = """
                INSERT INTO events (name, status, start_date, end_date, capacity, registered_count)
                SELECT 'Test', 'SCHEDULED', v.start_date::timestamp, v.end_date::timestamp, v.capacity, v.registered_count
                FROM (VALUES %s) AS v(start_date, end_date, capacity, registered_count)
                """.formatted(values);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql));
    }
}
