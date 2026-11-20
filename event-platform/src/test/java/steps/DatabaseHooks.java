package steps;

import com.eventplatform.api.inmemory.InMemoryDatabase;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Every scenario starts with empty in-memory tables and ids from 1. The acceptance profile runs on the
 * in-memory repositories, whose transactions cannot be rolled back, so this replaces the old rollback.
 */
public class DatabaseHooks {

    @Autowired private InMemoryDatabase database;

    @Before(order = 0)
    public void clearDatabase() {
        database.clear();
    }
}
