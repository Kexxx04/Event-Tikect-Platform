package com.eventplatform.e2e.utils;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The API keeps its H2 data while it is running and rejects repeated emails and documents (409),
 * so every name, email and document created by the tests carries a suffix unique to this run.
 */
public final class UniqueData {

    private static final String RUN_ID = Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private UniqueData() {
    }

    /** For example "mhb3x0k27": the run id followed by a sequence number. */
    public static String suffix() {
        return RUN_ID + SEQUENCE.incrementAndGet();
    }
}
