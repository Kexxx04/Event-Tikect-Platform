package com.eventplatform.e2e.utils;

import java.time.ZoneId;
import net.serenitybdd.model.environment.EnvironmentSpecificConfiguration;
import net.thucydides.model.environment.SystemEnvironmentVariables;

/** Values of the "e2e" block of serenity.conf (each one can be overridden with an environment variable). */
public final class TestConfiguration {

    private TestConfiguration() {
    }

    public static String adminUsername() {
        return property("e2e.admin.username");
    }

    public static String adminPassword() {
        return property("e2e.admin.password");
    }

    /** Time zone of the API server, used to build event dates relative to "now". */
    public static ZoneId serverTimeZone() {
        return ZoneId.of(property("e2e.timezone"));
    }

    private static String property(String name) {
        return EnvironmentSpecificConfiguration.from(SystemEnvironmentVariables.currentEnvironmentVariables())
                .getOptionalProperty(name)
                .orElseThrow(() -> new IllegalStateException("Missing property '" + name + "' in serenity.conf"));
    }
}
