package com.eventplatform.e2e.models;

/** Event states of the API, the label the frontend shows and whether the change asks for confirmation. */
public enum EventStatus {
    SCHEDULED("Scheduled", false),
    ACTIVE("Active", false),
    FINISHED("Finished", true),
    CANCELLED("Cancelled", true);

    private final String label;
    private final boolean confirmationRequired;

    EventStatus(String label, boolean confirmationRequired) {
        this.label = label;
        this.confirmationRequired = confirmationRequired;
    }

    public String label() {
        return label;
    }

    public boolean confirmationRequired() {
        return confirmationRequired;
    }

    /** Value used in the data-testid of its status button, e.g. "active". */
    public String actionId() {
        return name().toLowerCase();
    }
}
