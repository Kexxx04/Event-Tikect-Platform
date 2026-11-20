package com.eventplatform.api.inmemory;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transaction manager of the inmemory profile, so that {@code @Transactional} keeps its semantics without a
 * database: propagation (REQUIRED joins the current transaction, MANDATORY fails without one, REQUIRES_NEW
 * suspends it), read-only flag, rollback-only marks and transaction synchronizations. The in-memory
 * repositories rely on the synchronizations to hold the event row locks until the transaction ends and to
 * check the schema constraints before commit.
 *
 * <p><strong>Limitation:</strong> a rollback does NOT undo changes. The repositories write to the maps
 * immediately and the entities are the stored instances, so whatever a transaction changed before failing
 * stays in memory. The services validate every business rule before changing anything, and each test calls
 * {@link InMemoryDatabase#clear()} first, so tests do not depend on rollbacks.
 */
@Component("transactionManager")
@Profile("inmemory")
public class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new InMemoryTransaction((TransactionState) TransactionSynchronizationManager.getResource(this));
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((InMemoryTransaction) transaction).state != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        TransactionState state = new TransactionState();
        ((InMemoryTransaction) transaction).state = state;
        TransactionSynchronizationManager.bindResource(this, state);
    }

    @Override
    protected Object doSuspend(Object transaction) {
        ((InMemoryTransaction) transaction).state = null;
        return TransactionSynchronizationManager.unbindResource(this);
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        TransactionSynchronizationManager.bindResource(this, suspendedResources);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        // Nothing to write: the repositories already stored every change.
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // Nothing to undo (see the limitation in the class comment).
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((InMemoryTransaction) status.getTransaction()).state.rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        TransactionSynchronizationManager.unbindResource(this);
    }

    /** Shared by every method that takes part in the same physical transaction. */
    private static final class TransactionState {
        private volatile boolean rollbackOnly;
    }

    private static final class InMemoryTransaction implements SmartTransactionObject {

        private TransactionState state;

        private InMemoryTransaction(TransactionState state) {
            this.state = state;
        }

        @Override
        public boolean isRollbackOnly() {
            return state != null && state.rollbackOnly;
        }

        @Override
        public void flush() {
            // Nothing is buffered.
        }
    }
}
