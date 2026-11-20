package com.eventplatform.api.inmemory;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * Constraint violations of V1__create_initial_schema.sql. They throw DataIntegrityViolationException with
 * the message PostgreSQL gives, which is what Spring throws when the real database rejects a row.
 */
final class SchemaConstraints {

    private SchemaConstraints() {
    }

    static void notNull(Object value, String table, String column) {
        if (value == null) {
            throw new DataIntegrityViolationException("null value in column \"" + column + "\" of relation \""
                    + table + "\" violates not-null constraint");
        }
    }

    static void check(boolean satisfied, String table, String constraint) {
        if (!satisfied) {
            throw new DataIntegrityViolationException("new row for relation \"" + table
                    + "\" violates check constraint \"" + constraint + "\"");
        }
    }

    static void unique(boolean duplicated, String constraint) {
        if (duplicated) {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"" + constraint + "\"");
        }
    }

    static void foreignKey(boolean referencedRowExists, String table, String constraint) {
        if (!referencedRowExists) {
            throw new DataIntegrityViolationException("insert or update on table \"" + table
                    + "\" violates foreign key constraint \"" + constraint + "\"");
        }
    }

    static void notReferenced(boolean referenced, String table, String constraint, String referencingTable) {
        if (referenced) {
            throw new DataIntegrityViolationException("update or delete on table \"" + table
                    + "\" violates foreign key constraint \"" + constraint + "\" on table \""
                    + referencingTable + "\"");
        }
    }
}
