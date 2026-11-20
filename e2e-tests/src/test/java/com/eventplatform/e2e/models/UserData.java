package com.eventplatform.e2e.models;

import com.eventplatform.e2e.utils.UniqueData;
import java.text.Normalizer;
import java.util.Locale;

/** A user typed into the "New user" form. The email and document are unique for every run. */
public record UserData(String name, String email, String document) {

    public static UserData named(String name) {
        String suffix = UniqueData.suffix();
        return new UserData(name, slug(name) + "." + suffix + "@example.com", "E2E-" + suffix.toUpperCase(Locale.ROOT));
    }

    /** Same person data with another email, used to try a duplicated email. */
    public UserData withEmail(String otherEmail) {
        return new UserData(name, otherEmail, document);
    }

    /** Text of the option in the "User to register" list of the event page. */
    public String registrationOption() {
        return name + " (" + email + ")";
    }

    private static String slug(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".").replaceAll("(^\\.)|(\\.$)", "");
    }
}
