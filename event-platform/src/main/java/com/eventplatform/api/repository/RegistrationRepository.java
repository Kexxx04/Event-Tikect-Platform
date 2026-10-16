package com.eventplatform.api.repository;

import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);
}
