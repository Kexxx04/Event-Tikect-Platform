package com.eventplatform.api.inmemory;

import static com.eventplatform.api.inmemory.TestRows.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class InMemoryTransactionManagerTests {

    private final InMemoryTransactionManager transactionManager = new InMemoryTransactionManager();
    private final TransactionTemplate required = template(TransactionDefinition.PROPAGATION_REQUIRED);

    private TransactionTemplate template(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
    }

    @Test
    void startsARealTransactionWithSynchronizations() {
        required.executeWithoutResult(status -> {
            assertTrue(status.isNewTransaction());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
        });
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void requiredJoinsTheTransactionThatIsAlreadyRunning() {
        required.executeWithoutResult(outer ->
                required.executeWithoutResult(inner -> assertFalse(inner.isNewTransaction())));
    }

    @Test
    void mandatoryFailsWithoutATransactionAndJoinsAnExistingOne() {
        TransactionTemplate mandatory = template(TransactionDefinition.PROPAGATION_MANDATORY);

        assertThrows(IllegalTransactionStateException.class, () -> mandatory.executeWithoutResult(status -> { }));
        required.executeWithoutResult(outer ->
                mandatory.executeWithoutResult(inner -> assertFalse(inner.isNewTransaction())));
    }

    @Test
    void requiresNewSuspendsTheCurrentTransactionAndResumesItAfterwards() {
        List<String> completed = new ArrayList<>();
        TransactionTemplate requiresNew = template(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        required.executeWithoutResult(outer -> {
            register(completed, "outer");
            requiresNew.executeWithoutResult(inner -> {
                assertTrue(inner.isNewTransaction());
                register(completed, "inner");
            });
            assertEquals(List.of("inner"), completed);
        });

        assertEquals(List.of("inner", "outer"), completed);
    }

    @Test
    void runsTheSynchronizationsOnCommit() {
        List<String> calls = new ArrayList<>();

        required.executeWithoutResult(status -> TransactionSynchronizationManager.registerSynchronization(
                new RecordingSynchronization(calls)));

        assertEquals(List.of("beforeCommit", "afterCompletion:committed"), calls);
    }

    @Test
    void runsOnlyAfterCompletionOnRollback() {
        List<String> calls = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> required.executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new RecordingSynchronization(calls));
            throw new IllegalStateException("business rule violated");
        }));

        assertEquals(List.of("afterCompletion:rolled back"), calls);
    }

    @Test
    void aParticipantThatFailsMarksTheWholeTransactionRollbackOnly() {
        assertThrows(UnexpectedRollbackException.class, () -> required.executeWithoutResult(outer ->
                required.executeWithoutResult(inner -> inner.setRollbackOnly())));
    }

    @Test
    void exposesTheReadOnlyFlag() {
        TransactionTemplate readOnly = template(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnly.setReadOnly(true);

        readOnly.executeWithoutResult(status ->
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
        required.executeWithoutResult(status ->
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
    }

    /** Documented limitation: the in-memory tables are written immediately and a rollback keeps the changes. */
    @Test
    void doesNotUndoChangesOnRollback() {
        InMemoryUserRepository users = new InMemoryUserRepository(new InMemoryDatabase());

        assertThrows(IllegalStateException.class, () -> required.executeWithoutResult(status -> {
            users.save(user("ada"));
            throw new IllegalStateException("business rule violated");
        }));

        assertTrue(users.existsByEmailIgnoreCase("ada@example.com"));
    }

    private static void register(List<String> completed, String name) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                completed.add(name);
            }
        });
    }

    private record RecordingSynchronization(List<String> calls) implements TransactionSynchronization {

        @Override
        public void beforeCommit(boolean readOnly) {
            calls.add("beforeCommit");
        }

        @Override
        public void afterCompletion(int status) {
            calls.add("afterCompletion:" + (status == STATUS_COMMITTED ? "committed" : "rolled back"));
        }
    }
}
