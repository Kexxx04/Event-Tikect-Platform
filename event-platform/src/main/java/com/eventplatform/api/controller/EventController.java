package com.eventplatform.api.controller;

import com.eventplatform.api.dto.event.CreateEventRequest;
import com.eventplatform.api.dto.event.EventAvailabilityResponse;
import com.eventplatform.api.dto.event.EventResponse;
import com.eventplatform.api.dto.event.UpdateEventRequest;
import com.eventplatform.api.dto.event.UpdateEventStatusRequest;
import com.eventplatform.api.service.EventService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.create(request);
        return ResponseEntity
                .created(URI.create("/api/events/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<EventResponse> findAll() {
        return eventService.findAll();
    }

    @GetMapping("/{id}")
    public EventResponse findById(@PathVariable Long id) {
        return eventService.findById(id);
    }

    @GetMapping("/{id}/availability")
    public EventAvailabilityResponse getAvailability(@PathVariable Long id) {
        return eventService.getAvailability(id);
    }

    @PutMapping("/{id}")
    public EventResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request
    ) {
        return eventService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public EventResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventStatusRequest request
    ) {
        return eventService.updateStatus(id, request.status());
    }
}
