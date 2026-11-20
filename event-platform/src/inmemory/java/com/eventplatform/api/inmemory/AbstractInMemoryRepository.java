package com.eventplatform.api.inmemory;

import jakarta.persistence.Id;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * JpaRepository on top of one table of {@link InMemoryDatabase}. It implements the methods that the services
 * and the tests use; every other method throws UnsupportedOperationException.
 *
 * <p>Like a JPA persistence context, it returns the stored instances, so changing an entity changes the row.
 * The schema constraints are checked on {@code save} and, for the entities that a read-write transaction
 * read or saved, again just before commit, which is when Hibernate flushes those changes to PostgreSQL.
 */
abstract class AbstractInMemoryRepository<T> implements JpaRepository<T, Long> {

    private final InMemoryDatabase.Table<T> table;
    private final Class<T> entityType;
    private final Field idField;

    protected AbstractInMemoryRepository(InMemoryDatabase.Table<T> table, Class<T> entityType) {
        this.table = table;
        this.entityType = entityType;
        this.idField = findIdField(entityType);
    }

    /**
     * Throws DataIntegrityViolationException when the row breaks a constraint of its table. It runs while
     * holding the write lock of the table, so two rows can never pass a unique check at the same time.
     */
    protected abstract void checkConstraints(T row);

    /** Throws DataIntegrityViolationException when a foreign key of another table still points to the row. */
    protected void checkNotReferenced(Long id) {
        // No table references this one unless a subclass says otherwise.
    }

    // ---------------------------------------------------------------- methods used by services and tests

    @Override
    public <S extends T> S save(S entity) {
        Assert.notNull(entity, "Entity must not be null");
        table.write(() -> {
            checkConstraints(entity);
            Long id = idOf(entity);
            if (id == null) {
                id = table.nextId();
                // Same as IDENTITY: the entities keep their id without a new public setter.
                ReflectionUtils.setField(idField, entity, id);
            } else {
                table.advanceSequenceTo(id);
            }
            table.rows().put(id, entity);
        });
        return managed(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        Assert.notNull(entities, "Entities must not be null");
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public Optional<T> findById(Long id) {
        Assert.notNull(id, "The given id must not be null");
        return Optional.ofNullable(table.rows().get(id)).map(this::managed);
    }

    @Override
    public boolean existsById(Long id) {
        Assert.notNull(id, "The given id must not be null");
        return table.rows().containsKey(id);
    }

    /** Ordered by id, so the result does not depend on the hash map. */
    @Override
    public List<T> findAll() {
        return query(row -> true, byId());
    }

    @Override
    public List<T> findAll(Sort sort) {
        Assert.notNull(sort, "Sort must not be null");
        return query(row -> true, comparatorOf(sort));
    }

    @Override
    public long count() {
        return table.rows().size();
    }

    /** A missing id is ignored, as in Spring Data JPA. */
    @Override
    public void deleteById(Long id) {
        Assert.notNull(id, "The given id must not be null");
        deleteRows(List.of(id));
    }

    @Override
    public void deleteAllInBatch(Iterable<T> entities) {
        Assert.notNull(entities, "Entities must not be null");
        List<Long> ids = new ArrayList<>();
        entities.forEach(entity -> ids.add(idOf(entity)));
        deleteRows(ids);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        Assert.notNull(ids, "Ids must not be null");
        List<Long> idList = new ArrayList<>();
        ids.forEach(idList::add);
        deleteRows(idList);
    }

    // ---------------------------------------------------------------- helpers for the subclasses

    protected Stream<T> rows() {
        return table.rows().values().stream();
    }

    /** Every row except the given one (by id), for the unique constraints. */
    protected Stream<T> otherRows(T row) {
        Long id = idOf(row);
        return table.rows().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(id))
                .map(Map.Entry::getValue);
    }

    /** Rows that match the condition, in the given order, as a modifiable list like Hibernate returns. */
    protected List<T> query(Predicate<? super T> condition, Comparator<? super T> order) {
        return query(condition, order, Long.MAX_VALUE);
    }

    /** Same as {@link #query(Predicate, Comparator)} with a LIMIT. */
    protected List<T> query(Predicate<? super T> condition, Comparator<? super T> order, long maxResults) {
        return rows()
                .filter(condition)
                .sorted(order)
                .limit(maxResults)
                .map(this::managed)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    protected Long idOf(Object entity) {
        return (Long) ReflectionUtils.getField(idField, entity);
    }

    protected Comparator<T> byId() {
        return Comparator.comparing(this::idOf);
    }

    /** The synchronization of the current transaction that matches, or a new one registered in it. */
    static <S extends TransactionSynchronization> S currentSynchronization(
            Class<S> type, Predicate<S> matches, Supplier<S> factory) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (type.isInstance(synchronization) && matches.test(type.cast(synchronization))) {
                return type.cast(synchronization);
            }
        }
        S created = factory.get();
        TransactionSynchronizationManager.registerSynchronization(created);
        return created;
    }

    static boolean isReadWriteTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    // ---------------------------------------------------------------- internals

    private void deleteRows(List<Long> ids) {
        // One statement: if any row is still referenced, nothing is deleted.
        table.write(() -> {
            ids.stream().filter(table.rows()::containsKey).forEach(this::checkNotReferenced);
            ids.forEach(table.rows()::remove);
        });
    }

    /** Remembers the entity so its constraints are checked again before the transaction commits. */
    private <S extends T> S managed(S entity) {
        if (isReadWriteTransactionActive()) {
            currentSynchronization(FlushOnCommit.class, flush -> flush.repository == this,
                    () -> new FlushOnCommit(this)).entities.add(entity);
        }
        return entity;
    }

    private void checkBeforeCommit(Object entity) {
        T row = entityType.cast(entity);
        table.write(() -> checkConstraints(row));
    }

    private Comparator<T> comparatorOf(Sort sort) {
        Comparator<T> comparator = (left, right) -> 0;
        for (Sort.Order order : sort) {
            Function<T, Comparable<Object>> property = row -> propertyOf(row, order.getProperty());
            // PostgreSQL default: nulls last when ascending, first when descending.
            Comparator<T> byProperty = Comparator.comparing(property, Comparator.nullsLast(Comparator.naturalOrder()));
            comparator = comparator.thenComparing(order.isAscending() ? byProperty : byProperty.reversed());
        }
        return comparator.thenComparing(byId());
    }

    @SuppressWarnings("unchecked")
    private static Comparable<Object> propertyOf(Object row, String property) {
        return (Comparable<Object>) PropertyAccessorFactory.forBeanPropertyAccess(row).getPropertyValue(property);
    }

    private static Field findIdField(Class<?> entityType) {
        List<Field> idFields = new ArrayList<>();
        ReflectionUtils.doWithFields(entityType, idFields::add, field -> field.isAnnotationPresent(Id.class));
        Assert.state(idFields.size() == 1, () -> entityType.getName() + " must have exactly one @Id field");
        Field idField = idFields.get(0);
        ReflectionUtils.makeAccessible(idField);
        return idField;
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException(method + " is not used by the application, so the in-memory "
                + "repository does not implement it");
    }

    /** Entities read or saved by one transaction; Hibernate would flush their changes before commit. */
    private static final class FlushOnCommit implements TransactionSynchronization {

        private final AbstractInMemoryRepository<?> repository;
        private final Set<Object> entities = Collections.newSetFromMap(new IdentityHashMap<>());

        private FlushOnCommit(AbstractInMemoryRepository<?> repository) {
            this.repository = repository;
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            entities.forEach(repository::checkBeforeCommit);
        }
    }

    // ---------------------------------------------------------------- methods the application does not use

    @Override
    public void flush() {
        throw unsupported("flush");
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        throw unsupported("saveAndFlush");
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        throw unsupported("saveAllAndFlush");
    }

    @Override
    public void deleteAllInBatch() {
        throw unsupported("deleteAllInBatch()");
    }

    @Override
    @SuppressWarnings("deprecation")
    public T getOne(Long id) {
        throw unsupported("getOne");
    }

    @Override
    @SuppressWarnings("deprecation")
    public T getById(Long id) {
        throw unsupported("getById");
    }

    @Override
    public T getReferenceById(Long id) {
        throw unsupported("getReferenceById");
    }

    @Override
    public List<T> findAllById(Iterable<Long> ids) {
        throw unsupported("findAllById");
    }

    @Override
    public void delete(T entity) {
        throw unsupported("delete");
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        throw unsupported("deleteAllById");
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        throw unsupported("deleteAll(Iterable)");
    }

    @Override
    public void deleteAll() {
        throw unsupported("deleteAll()");
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        throw unsupported("findAll(Pageable)");
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        throw unsupported("findOne(Example)");
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        throw unsupported("findAll(Example)");
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        throw unsupported("findAll(Example, Sort)");
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        throw unsupported("findAll(Example, Pageable)");
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        throw unsupported("count(Example)");
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        throw unsupported("exists(Example)");
    }

    @Override
    public <S extends T, R> R findBy(Example<S> example,
                                     Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        throw unsupported("findBy(Example, Function)");
    }
}
