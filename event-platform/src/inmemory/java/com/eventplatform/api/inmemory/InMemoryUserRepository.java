package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.SchemaConstraints.notNull;
import static com.eventplatform.api.inmemory.SchemaConstraints.notReferenced;
import static com.eventplatform.api.inmemory.SchemaConstraints.unique;

import com.eventplatform.api.model.User;
import com.eventplatform.api.repository.UserRepository;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Fake of {@link UserRepository} over the users table of {@link InMemoryDatabase}. */
@Repository
@Profile("inmemory")
public class InMemoryUserRepository extends AbstractInMemoryRepository<User> implements UserRepository {

    private final InMemoryDatabase database;

    public InMemoryUserRepository(InMemoryDatabase database) {
        super(database.users(), User.class);
        this.database = database;
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        return rows().anyMatch(hasEmail(email));
    }

    @Override
    public boolean existsByDocument(String document) {
        return rows().anyMatch(hasDocument(document));
    }

    @Override
    public boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id) {
        return rows().filter(isNot(id)).anyMatch(hasEmail(email));
    }

    @Override
    public boolean existsByDocumentAndIdNot(String document, Long id) {
        return rows().filter(isNot(id)).anyMatch(hasDocument(document));
    }

    /**
     * NOT NULL columns and the unique email and document. PostgreSQL compares the email exactly, but
     * UserService always stores it in lower case, so comparing without case gives the same result.
     */
    @Override
    protected void checkConstraints(User user) {
        notNull(user.getName(), "users", "name");
        notNull(user.getEmail(), "users", "email");
        notNull(user.getDocument(), "users", "document");
        notNull(user.getStatus(), "users", "status");
        unique(otherRows(user).anyMatch(hasEmail(user.getEmail())), "users_email_key");
        unique(otherRows(user).anyMatch(hasDocument(user.getDocument())), "users_document_key");
    }

    @Override
    protected void checkNotReferenced(Long id) {
        boolean registered = database.registrations().rows().values().stream()
                .anyMatch(registration -> id.equals(registration.getUser().getId()));
        notReferenced(registered, "users", "fk_registrations_user", "registrations");
    }

    private static Predicate<User> hasEmail(String email) {
        return user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(email);
    }

    private static Predicate<User> hasDocument(String document) {
        return user -> user.getDocument() != null && user.getDocument().equals(document);
    }

    // "IdNot" with a null id becomes "id IS NOT NULL" in Spring Data JPA, which matches every user.
    private static Predicate<User> isNot(Long id) {
        return user -> !Objects.equals(user.getId(), id);
    }
}
