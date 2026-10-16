package com.eventplatform.api.repository;

import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    Optional<Registration> findByEventIdAndUserId(Long eventId, Long userId);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdOrderByRegistrationDateAscIdAsc(Long eventId);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(Long eventId, RegistrationStatus status);

    // Oldest first: the waitlist is served by registration date, then by id.
    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
            Long eventId, RegistrationStatus status, Limit limit);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByUserIdOrderByEventStartDateAscIdAsc(Long userId);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByUserIdAndStatusOrderByEventStartDateAscIdAsc(Long userId, RegistrationStatus status);
}
