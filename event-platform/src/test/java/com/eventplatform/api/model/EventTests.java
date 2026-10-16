package com.eventplatform.api.model;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventplatform.api.model.enums.EventStatus;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventTests {
    private static Event validEvent() {
        LocalDateTime start = LocalDateTime.now().plusDays(10);
        return new Event("Conference", "Talks", start, start.plusHours(2), 10, EventStatus.SCHEDULED);
    }

    @Test
    void acceptsAnEventWithNoRegistrations() throws Exception {
        assertTrue(validEvent().validateForPublication());
    }

    @Test
    void acceptsAnEventAtCapacityAndMaximumDescriptionLength() throws Exception {
        Event event = validEvent();
        event.setName("ABC");
        event.setDescription("a".repeat(1000));
        event.setRegisteredCount(10);
        assertTrue(event.validateForPublication());
    }

    @Test
    void acceptsAnOptionalDescription() throws Exception {
        Event event = validEvent();
        event.setDescription(null);
        assertTrue(event.validateForPublication());
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void rejectsInvalidPublicationData(Consumer<Event> change) {
        Event event = validEvent();
        change.accept(event);
        assertThrows(Exception.class, event::validateForPublication);
    }

    static Stream<Consumer<Event>> invalidEvents() {
        return Stream.of(
                event -> event.setName(null),
                event -> event.setName("  "),
                event -> event.setName("AB"),
                event -> event.setDescription("a".repeat(1001)),
                event -> event.setStartDate(null),
                event -> event.setStartDate(LocalDateTime.now().minusDays(1)),
                event -> event.setEndDate(null),
                event -> event.setEndDate(event.getStartDate().minusSeconds(1)),
                event -> event.setEndDate(event.getStartDate()),
                event -> event.setCapacity(null),
                event -> event.setCapacity(0),
                event -> event.setCapacity(-1),
                event -> event.setRegisteredCount(null),
                event -> event.setRegisteredCount(-1),
                event -> event.setRegisteredCount(11),
                event -> event.setStatus(null));
    }
}
