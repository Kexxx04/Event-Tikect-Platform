package com.eventplatform.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ClockConfigTests {

    @Test
    void providesTheSystemClockInTheDefaultZone() {
        Clock clock = new ClockConfig().clock();
        assertEquals(ZoneId.systemDefault(), clock.getZone());
    }
}
