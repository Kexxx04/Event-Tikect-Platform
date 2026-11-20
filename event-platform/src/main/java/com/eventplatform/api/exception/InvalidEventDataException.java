package com.eventplatform.api.exception;

public class InvalidEventDataException extends RuntimeException {

    public InvalidEventDataException(String message) {
        super(message);
    }
}
