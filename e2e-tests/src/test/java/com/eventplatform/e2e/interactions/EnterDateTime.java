package com.eventplatform.e2e.interactions;

import static net.serenitybdd.screenplay.Tasks.instrumented;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Interaction;
import net.serenitybdd.screenplay.abilities.BrowseTheWeb;
import net.serenitybdd.screenplay.targets.Target;

/**
 * Fills an {@code <input type="datetime-local">}. Typing into that control depends on the browser
 * language and the operating system date format, so the value is set in its ISO form
 * ("yyyy-MM-ddTHH:mm") and the "input" event is fired, which is what React listens to.
 */
public class EnterDateTime implements Interaction {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // React tracks the value of controlled inputs, so it has to be set through the native setter.
    private static final String SET_INPUT_VALUE = """
            const input = arguments[0];
            const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
            setValue.call(input, arguments[1]);
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.dispatchEvent(new Event('change', { bubbles: true }));
            """;

    private final String value;
    private final Target field;

    public EnterDateTime(String value, Target field) {
        this.value = value;
        this.field = field;
    }

    public static Builder of(LocalDateTime dateTime) {
        return new Builder(dateTime.format(INPUT_FORMAT));
    }

    @Override
    @Step("{0} enters '#value' into the #field")
    public <T extends Actor> void performAs(T actor) {
        BrowseTheWeb.as(actor).evaluateJavascript(SET_INPUT_VALUE, field.resolveFor(actor).getElement(), value);
    }

    public record Builder(String value) {
        public EnterDateTime into(Target field) {
            return instrumented(EnterDateTime.class, value, field);
        }
    }
}
