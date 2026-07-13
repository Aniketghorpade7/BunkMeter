package com.bunkmeter.app.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/**
 * Release-build stub.
 *
 * <p>The real {@code MockDataGenerator} (which wipes the database and injects three
 * months of fake attendance) lives only in {@code src/debug/java}. This no-op stub
 * has the identical public API so callers in the {@code main} source set still
 * compile for release, but the test-data logic can never ship to or run for a real
 * user.</p>
 */
public class MockDataGenerator {

    /** No-op in release builds. */
    public static void injectDummyData(Context context, Activity activity) {
        if (context != null) {
            Toast.makeText(context, "Developer tools are disabled in this build.",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
