package com.eventplatform.api.controller;

import com.eventplatform.api.dto.registration.CreateRegistrationRequest;
import com.eventplatform.api.dto.registration.RegistrationCancellationResponse;
import com.eventplatform.api.dto.registration.RegistrationResponse;
import com.eventplatform.api.dto.registration.UserEventResponse;
import com.eventplatform.api.dto.registration.WaitlistEntryResponse;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.service.RegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/events/{eventId}/registrations")
    public ResponseEntity<RegistrationResponse> register(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateRegistrationRequest request
    ) {
        RegistrationResponse response = registrationService.register(eventId, request.userId());
        return ResponseEntity
                .created(URI.create("/api/events/" + eventId + "/registrations/" + response.userId()))
                .body(response);
    }

    @GetMapping("/events/{eventId}/registrations")
    public List<RegistrationResponse> findByEvent(
            @PathVariable Long eventId,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        return registrationService.findByEvent(eventId, status);
    }

    @GetMapping("/events/{eventId}/registrations/{userId}")
    public RegistrationResponse findOne(@PathVariable Long eventId, @PathVariable Long userId) {
        return registrationService.findOne(eventId, userId);
    }

    @DeleteMapping("/events/{eventId}/registrations/{userId}")
    public RegistrationCancellationResponse cancel(@PathVariable Long eventId, @PathVariable Long userId) {
        return registrationService.cancel(eventId, userId);
    }

    @GetMapping("/events/{eventId}/waitlist")
    public List<WaitlistEntryResponse> findWaitlist(@PathVariable Long eventId) {
        return registrationService.findWaitlist(eventId);
    }

    @GetMapping("/users/{userId}/events")
    public List<UserEventResponse> findEventsOfUser(
            @PathVariable Long userId,
            @RequestParam(required = false) RegistrationStatus status
    ) {
        return registrationService.findEventsOfUser(userId, status);
    }
}
