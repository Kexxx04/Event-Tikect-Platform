package com.eventplatform.api.service;

import com.eventplatform.api.model.Event;
import com.eventplatform.api.model.Registration;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.repository.RegistrationRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaitlistService {

    private final RegistrationRepository registrationRepository;

    public WaitlistService(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * Confirms the oldest waitlisted registrations (by registration date, then id) until the event
     * is full. The caller must hold the PESSIMISTIC_WRITE lock of the event in its transaction.
     *
     * @return the promoted registrations, in promotion order
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Registration> fillFreeSeats(Event event) {
        int freeSeats = event.getCapacity() - event.getRegisteredCount();
        if (freeSeats <= 0) {
            return List.of();
        }

        List<Registration> promoted = registrationRepository.findByEventIdAndStatusOrderByRegistrationDateAscIdAsc(
                event.getId(), RegistrationStatus.WAITING_LIST, Limit.of(freeSeats));
        promoted.forEach(registration -> registration.setStatus(RegistrationStatus.CONFIRMED));
        event.setRegisteredCount(event.getRegisteredCount() + promoted.size());
        return promoted;
    }
}
