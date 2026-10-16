package com.eventplatform.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({UserNotFoundException.class, EventNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler({
            DuplicateUserException.class,
            BusinessRuleViolationException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<ApiError> handleConflict(
            Exception exception,
            HttpServletRequest request
    ) {
        String message = exception instanceof DataIntegrityViolationException
                ? "Email or document is already registered"
                : exception.getMessage();
        return buildError(HttpStatus.CONFLICT, message, request, Map.of());
    }

    @ExceptionHandler({InvalidUserDataException.class, InvalidEventDataException.class})
    public ResponseEntity<ApiError> handleInvalidData(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildError(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                details.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        return buildError(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                details
        );
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, String> details
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(error);
    }
}
