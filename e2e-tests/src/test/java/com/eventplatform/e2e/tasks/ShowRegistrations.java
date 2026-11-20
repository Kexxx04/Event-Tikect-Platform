package com.eventplatform.e2e.tasks;

import com.eventplatform.e2e.models.RegistrationStatus;
import com.eventplatform.e2e.userinterfaces.RegistrationsSection;
import net.serenitybdd.screenplay.Performable;
import net.serenitybdd.screenplay.Task;
import net.serenitybdd.screenplay.actions.Click;

/** The registrations table shows one status at a time; this clicks the matching filter pill. */
public final class ShowRegistrations {

    private ShowRegistrations() {
    }

    public static Performable withStatus(RegistrationStatus status) {
        return Task.where("{0} shows the " + status + " registrations",
                Click.on(RegistrationsSection.FILTER.of(status.filterId())));
    }
}
