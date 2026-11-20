package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.TestRows.START;
import static com.eventplatform.api.inmemory.TestRows.event;
import static com.eventplatform.api.inmemory.TestRows.registration;
import static com.eventplatform.api.inmemory.TestRows.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventplatform.api.model.User;
import com.eventplatform.api.model.enums.RegistrationStatus;
import com.eventplatform.api.model.enums.UserStatus;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Contract of InMemoryUserRepository, including the generic JpaRepository methods of its base class. */
class InMemoryUserRepositoryTests {

    private final InMemoryDatabase database = new InMemoryDatabase();
    private final InMemoryUserRepository users = new InMemoryUserRepository(database);

    // ---------------------------------------------------------------- ids and generic methods

    @Test
    void assignsSequentialIdsLikeAnIdentityColumnAndReturnsTheStoredInstance() {
        User ada = user("ada");
        User saved = users.save(ada);

        assertSame(ada, saved);
        assertEquals(1L, ada.getId());
        assertEquals(2L, users.save(user("bob")).getId());
        assertSame(ada, users.findById(1L).orElseThrow());
    }

    @Test
    void savingAStoredUserAgainUpdatesItsRowWithoutANewId() {
        User ada = users.save(user("ada"));
        ada.setName("Ada Byron");

        users.save(ada);

        assertEquals(1, users.count());
        assertEquals(1L, ada.getId());
        assertEquals("Ada Byron", users.findById(1L).orElseThrow().getName());
    }

    @Test
    void keepsAnExplicitIdAndNeverGeneratesItAgain() {
        User imported = user("imported");
        imported.setId(10L);

        users.save(imported);

        assertSame(imported, users.findById(10L).orElseThrow());
        assertEquals(11L, users.save(user("next")).getId());
    }

    @Test
    void savesAllUsersInOrder() {
        List<User> saved = users.saveAll(List.of(user("a"), user("b"), user("c")));

        assertEquals(List.of(1L, 2L, 3L), saved.stream().map(User::getId).toList());
        assertEquals(3, users.count());
    }

    @Test
    void findsExistsAndCountsById() {
        users.save(user("ada"));

        assertTrue(users.findById(1L).isPresent());
        assertTrue(users.findById(2L).isEmpty());
        assertTrue(users.existsById(1L));
        assertFalse(users.existsById(2L));
        assertEquals(1, users.count());
        assertThrows(IllegalArgumentException.class, () -> users.findById(null));
        assertThrows(IllegalArgumentException.class, () -> users.existsById(null));
    }

    @Test
    void listsByIdOrByTheRequestedSort() {
        users.save(new User("Carol", "carol@example.com", "3"));
        users.save(new User("Alice", "alice@example.com", "1"));
        users.save(new User("Bob", "bob@example.com", "2"));

        assertEquals(List.of("Carol", "Alice", "Bob"), names(users.findAll()));
        assertEquals(List.of("Carol", "Alice", "Bob"), names(users.findAll(Sort.by(Sort.Direction.ASC, "id"))));
        assertEquals(List.of("Bob", "Alice", "Carol"), names(users.findAll(Sort.by(Sort.Direction.DESC, "id"))));
        assertEquals(List.of("Alice", "Bob", "Carol"), names(users.findAll(Sort.by("name"))));
        assertEquals(List.of("Carol", "Bob", "Alice"), names(users.findAll(Sort.by("name").descending())));
    }

    @Test
    void sortsByASecondPropertyWhenTheFirstOneTies() {
        users.save(new User("Same", "b@example.com", "2"));
        users.save(new User("Same", "a@example.com", "1"));

        assertEquals(List.of(2L, 1L), ids(users.findAll(Sort.by("name", "email"))));
    }

    @Test
    void deletesByIdIgnoringMissingIds() {
        users.saveAll(List.of(user("a"), user("b")));

        users.deleteById(1L);
        users.deleteById(99L);

        assertEquals(List.of(2L), ids(users.findAll()));
    }

    @Test
    void deletesInBatchByIdAndByEntity() {
        List<User> saved = users.saveAll(List.of(user("a"), user("b"), user("c"), user("d")));

        users.deleteAllByIdInBatch(List.of(1L, 3L));
        users.deleteAllInBatch(List.of(saved.get(3)));

        assertEquals(List.of(2L), ids(users.findAll()));
    }

    // ---------------------------------------------------------------- derived queries

    @Test
    void findsEmailsIgnoringCaseAndDocumentsExactly() {
        users.save(user("ada"));

        assertTrue(users.existsByEmailIgnoreCase("ADA@Example.COM"));
        assertFalse(users.existsByEmailIgnoreCase("bob@example.com"));
        assertTrue(users.existsByDocument("DOC-ada"));
        assertFalse(users.existsByDocument("doc-ada"));
    }

    @Test
    void excludesTheGivenIdWhenCheckingDuplicates() {
        users.save(user("ada"));
        users.save(user("bob"));

        assertFalse(users.existsByEmailIgnoreCaseAndIdNot("ada@example.com", 1L));
        assertTrue(users.existsByEmailIgnoreCaseAndIdNot("ADA@example.com", 2L));
        assertFalse(users.existsByDocumentAndIdNot("DOC-ada", 1L));
        assertTrue(users.existsByDocumentAndIdNot("DOC-ada", 2L));
        // Spring Data JPA turns "IdNot" with null into "id IS NOT NULL".
        assertTrue(users.existsByEmailIgnoreCaseAndIdNot("ada@example.com", null));
        assertTrue(users.existsByDocumentAndIdNot("DOC-ada", null));
    }

    // ---------------------------------------------------------------- constraints of V1

    @Test
    void rejectsADuplicateEmailIgnoringCaseLikeTheUniqueIndex() {
        users.save(user("ada"));
        User duplicate = new User("Other", "ADA@EXAMPLE.COM", "OTHER");

        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> users.save(duplicate));

        assertTrue(exception.getMessage().contains("users_email_key"), exception.getMessage());
        assertNull(duplicate.getId());
        assertEquals(1, users.count());
    }

    @Test
    void rejectsADuplicateDocument() {
        users.save(user("ada"));

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> users.save(new User("Other", "other@example.com", "DOC-ada")));

        assertTrue(exception.getMessage().contains("users_document_key"), exception.getMessage());
        assertEquals(1, users.count());
    }

    @Test
    void rejectsChangingAStoredUserToTheEmailOfAnotherOne() {
        users.save(user("ada"));
        User bob = users.save(user("bob"));
        bob.setEmail("ada@example.com");

        assertThrows(DataIntegrityViolationException.class, () -> users.save(bob));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullColumns")
    void rejectsNullsInNotNullColumns(String column, Consumer<User> clearColumn) {
        User user = user("ada");
        clearColumn.accept(user);

        DataIntegrityViolationException exception =
                assertThrows(DataIntegrityViolationException.class, () -> users.save(user));

        assertEquals("null value in column \"" + column + "\" of relation \"users\" violates not-null constraint",
                exception.getMessage());
    }

    static Stream<Arguments> nullColumns() {
        return Stream.of(
                Arguments.of("name", (Consumer<User>) user -> user.setName(null)),
                Arguments.of("email", (Consumer<User>) user -> user.setEmail(null)),
                Arguments.of("document", (Consumer<User>) user -> user.setDocument(null)),
                Arguments.of("status", (Consumer<User>) user -> user.setStatus(null)));
    }

    @Test
    void refusesToDeleteAUserWithRegistrationsLikeTheForeignKey() {
        InMemoryEventRepository events = new InMemoryEventRepository(database);
        InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository(database);
        User registered = users.save(user("ada"));
        users.save(user("bob"));
        registrations.save(registration(registered, events.save(event(10)), START, RegistrationStatus.CONFIRMED));

        DataIntegrityViolationException exception = assertThrows(DataIntegrityViolationException.class,
                () -> users.deleteAllByIdInBatch(List.of(2L, 1L)));

        assertTrue(exception.getMessage().contains("fk_registrations_user"), exception.getMessage());
        // One statement: nothing was deleted.
        assertEquals(2, users.count());
        assertThrows(DataIntegrityViolationException.class, () -> users.deleteById(1L));
    }

    @Test
    void keepsTheStatusThatTheEntitySets() {
        User user = users.save(user("ada"));
        user.setStatus(UserStatus.INACTIVE);

        assertEquals(UserStatus.INACTIVE, users.findById(user.getId()).orElseThrow().getStatus());
    }

    // ---------------------------------------------------------------- methods the application does not use

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusedMethods")
    void throwsUnsupportedOperationExceptionForUnusedMethods(Executable call) {
        assertThrows(UnsupportedOperationException.class, call);
    }

    @SuppressWarnings("deprecation")
    static Stream<Named<Executable>> unusedMethods() {
        InMemoryUserRepository users = new InMemoryUserRepository(new InMemoryDatabase());
        User probe = user("probe");
        Example<User> example = Example.of(probe);
        return Stream.of(
                Named.of("flush", users::flush),
                Named.of("saveAndFlush", () -> users.saveAndFlush(probe)),
                Named.of("saveAllAndFlush", () -> users.saveAllAndFlush(List.of(probe))),
                Named.of("deleteAllInBatch()", users::deleteAllInBatch),
                Named.of("getOne", () -> users.getOne(1L)),
                Named.of("getById", () -> users.getById(1L)),
                Named.of("getReferenceById", () -> users.getReferenceById(1L)),
                Named.of("findAllById", () -> users.findAllById(List.of(1L))),
                Named.of("delete", () -> users.delete(probe)),
                Named.of("deleteAllById", () -> users.deleteAllById(List.of(1L))),
                Named.of("deleteAll(Iterable)", () -> users.deleteAll(List.of(probe))),
                Named.of("deleteAll()", users::deleteAll),
                Named.of("findAll(Pageable)", () -> users.findAll(Pageable.unpaged())),
                Named.of("findOne(Example)", () -> users.findOne(example)),
                Named.of("findAll(Example)", () -> users.findAll(example)),
                Named.of("findAll(Example, Sort)", () -> users.findAll(example, Sort.unsorted())),
                Named.of("findAll(Example, Pageable)", () -> users.findAll(example, Pageable.unpaged())),
                Named.of("count(Example)", () -> users.count(example)),
                Named.of("exists(Example)", () -> users.exists(example)),
                Named.of("findBy(Example, Function)", () -> users.findBy(example, query -> query.all())));
    }

    private static List<String> names(List<User> list) {
        return list.stream().map(User::getName).toList();
    }

    private static List<Long> ids(List<User> list) {
        return list.stream().map(User::getId).toList();
    }
}
