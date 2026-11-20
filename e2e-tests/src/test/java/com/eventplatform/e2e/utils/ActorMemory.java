package com.eventplatform.e2e.utils;

import com.eventplatform.e2e.models.CreatedEvent;
import com.eventplatform.e2e.models.UserData;
import net.serenitybdd.screenplay.Actor;

/** What the actor remembers between steps: the event of the scenario and the users it created. */
public final class ActorMemory {

    private static final String CURRENT_EVENT = "current event";
    private static final String USER_PREFIX = "user: ";

    private ActorMemory() {
    }

    public static void rememberEvent(Actor actor, CreatedEvent event) {
        actor.remember(CURRENT_EVENT, event);
    }

    public static CreatedEvent currentEvent(Actor actor) {
        CreatedEvent event = actor.recall(CURRENT_EVENT);
        if (event == null) {
            throw new IllegalStateException("No event was created in this scenario");
        }
        return event;
    }

    public static void rememberUser(Actor actor, UserData user) {
        actor.remember(USER_PREFIX + user.name(), user);
    }

    public static UserData user(Actor actor, String name) {
        UserData user = actor.recall(USER_PREFIX + name);
        if (user == null) {
            throw new IllegalStateException("The user '" + name + "' was not created in this scenario");
        }
        return user;
    }
}
