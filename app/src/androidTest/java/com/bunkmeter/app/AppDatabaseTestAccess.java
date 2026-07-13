package com.bunkmeter.app;

import com.bunkmeter.app.database.AppDatabase;

import java.lang.reflect.Field;

/**
 * Test-only helper that swaps {@link AppDatabase}'s private {@code INSTANCE}
 * singleton for an isolated in-memory database, so repository/worker tests never
 * touch the user's real {@code bunkmeter_db}.
 *
 * <p>This uses reflection <em>on purpose</em>: it keeps the test-only backdoor out
 * of production code (no {@code @VisibleForTesting} setter on {@code AppDatabase}).
 * Always call {@link #reset()} in {@code @After} so the next
 * {@code AppDatabase.getInstance(...)} rebuilds the real database.</p>
 */
final class AppDatabaseTestAccess {

    private AppDatabaseTestAccess() {}

    static void inject(AppDatabase db) {
        set(db);
    }

    static void reset() {
        set(null);
    }

    private static void set(AppDatabase db) {
        try {
            Field field = AppDatabase.class.getDeclaredField("INSTANCE");
            field.setAccessible(true);
            field.set(null, db);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject test AppDatabase INSTANCE", e);
        }
    }
}
