package com.eventplatform.e2e.models;

/** An event saved through the UI, with the id taken from its page URL (/events/{id}). */
public record CreatedEvent(long id, EventData data) {

    public String name() {
        return data.name();
    }

    public String path() {
        return "/events/" + id;
    }
}
