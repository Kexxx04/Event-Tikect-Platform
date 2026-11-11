package com.eventplatform.e2e.interactions;

import static net.serenitybdd.screenplay.Tasks.instrumented;

import net.serenitybdd.annotations.Step;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Interaction;
import net.serenitybdd.screenplay.targets.Target;

/** Types a password without writing its value in the Serenity report (Enter.theValue would show it). */
public class EnterSecret implements Interaction {

    private final String secret;
    private final Target field;

    public EnterSecret(String secret, Target field) {
        this.secret = secret;
        this.field = field;
    }

    public static Builder value(String secret) {
        return new Builder(secret);
    }

    @Override
    @Step("{0} enters a password into the #field")
    public <T extends Actor> void performAs(T actor) {
        field.resolveFor(actor).type(secret);
    }

    public record Builder(String secret) {
        public EnterSecret into(Target field) {
            return instrumented(EnterSecret.class, secret, field);
        }

        @Override
        public String toString() {
            return "Builder[secret=****]";
        }
    }
}
