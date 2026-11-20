package com.eventplatform.e2e.models;

/** Registration states of the API and the label the frontend shows for each one. */
public enum RegistrationStatus {
    CONFIRMED("Confirmed"),
    WAITING_LIST("Waitlisted"),
    CANCELLED("Cancelled");

    private final String label;

    RegistrationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Value of the data-testid of its filter pill, e.g. "waiting_list". */
    public String filterId() {
        return name().toLowerCase();
    }
}
