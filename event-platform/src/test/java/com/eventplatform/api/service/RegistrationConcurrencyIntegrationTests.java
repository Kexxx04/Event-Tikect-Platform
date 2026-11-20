package com.eventplatform.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eventplatform.api.dto.registration.RegistrationResponse;
import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.EventStatus;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.EventRepository;
import com.eventplatform.api.repository.RegistrationRepository;
import com.eventplatform.api.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stress scenario of the specification: 1000 users race for 100 seats. Each registration runs in
 * its own committed transaction, so the PESSIMISTIC_WRITE lock on the event row is really exercised.
 */
@SpringBootTest
@ActiveProfiles("test")
class RegistrationConcurrencyIntegrationTests {

    private static final int CAPACITY = 100;
    private static final int USERS = 1000;
    private static final int THREADS = 32;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    private Long eventId;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        LocalDateTime start = LocalDateTime.of(2030, 5, 10, 18, 0);
        eventId = eventRepository.save(
                new Event("Stress Conf", null, start, start.plusHours(3), CAPACITY, EventStatus.SCHEDULED)).getId();
        userIds = userRepository.saveAll(IntStream.range(0, USERS)
                        .mapToObj(index -> new User("Stress " + index, "stress" + index + "@example.com",
                                "STRESS-" + index))
                        .toList())
                .stream()
                .map(User::getId)
                .toList();
    }

    @AfterEach
    void cleanUp() {
        registrationRepository.deleteAllInBatch(registrationRepository.findByEventIdOrderByRegistrationDateAscIdAsc(eventId));
        userRepository.deleteAllByIdInBatch(userIds);
        eventRepository.deleteById(eventId);
    }

    @Test
    void neverExceedsTheCapacityWhenUsersRegisterConcurrently() throws Exception {
        List<RegistrationResponse> responses = runConcurrently(userIds.stream()
                .<Callable<RegistrationResponse>>map(userId -> () -> registrationService.register(eventId, userId))
                .toList());

        Map<RegistrationStatus, Long> byStatus = responses.stream()
                .collect(Collectors.groupingBy(RegistrationResponse::status, Collectors.counting()));
        assertEquals(CAPACITY, byStatus.get(RegistrationStatus.CONFIRMED));
        assertEquals(USERS - CAPACITY, byStatus.get(RegistrationStatus.WAITING_LIST));
        assertStoredState(CAPACITY, USERS - CAPACITY);
    }

    @Test
    void concurrentCancellationsPromoteExactlyOneWaitlistedUserPerReleasedSeat() throws Exception {
        runConcurrently(userIds.stream()
                .<Callable<RegistrationResponse>>map(userId -> () -> registrationService.register(eventId, userId))
                .toList());
        List<Long> confirmedUsers = registrationService.findByEvent(eventId, RegistrationStatus.CONFIRMED).stream()
                .map(RegistrationResponse::userId)
                .limit(20)
                .toList();

        List<Long> promotedUsers = runConcurrently(confirmedUsers.stream()
                        .<Callable<Long>>map(userId -> () -> registrationService.cancel(eventId, userId).promoted().userId())
                        .toList());

        assertEquals(20, promotedUsers.stream().distinct().count());
        assertStoredState(CAPACITY, USERS - CAPACITY - 20);
    }

    private void assertStoredState(int confirmed, int waitlisted) {
        assertEquals(confirmed, eventRepository.findById(eventId).orElseThrow().getRegisteredCount());
        assertEquals(confirmed, registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.CONFIRMED));
        assertEquals(waitlisted,
                registrationRepository.countByEventIdAndStatus(eventId, RegistrationStatus.WAITING_LIST));
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
