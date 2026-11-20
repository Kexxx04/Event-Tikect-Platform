package com.eventplatform.api.exception;

public class RegistrationNotFoundException extends RuntimeException {

    public RegistrationNotFoundException(Long eventId, Long userId) {
        super("Registration of user " + userId + " for event " + eventId + " was not found");
    }
}
