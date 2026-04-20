package com.bunkmeter.app.database;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lightweight, process-safe scratch pad for in-flight GPS readings.
 *
 * <h3>Change from boolean → integer scores</h3>
 * The old implementation stored a boolean per slot and counted how many were
 * {@code true}.  Now each slot holds a confidence <em>score</em> (0–100) so
 * {@link com.bunkmeter.app.scheduler.EvaluationWorker} can make smarter
 * decisions without adding extra storage or DB writes.
 *
 * <pre>
 *  Accuracy ≤  5 m  →  100 pts  (excellent / instant confirm)
 *  Accuracy ≤ 15 m  →   60 pts  (good)
 *  Accuracy ≤ 30 m  →   30 pts  (fair)
 *  Accuracy  > 30 m →   10 pts  (in-radius but low accuracy)
 *  Not in radius    →    0 pts
 * </pre>
 */
public class TempReadingStorage {

    private static final String PREF_PREFIX = "attendance_temp_";

    private static SharedPreferences getPrefs(Context context, long lectureId) {
        return context.getSharedPreferences(PREF_PREFIX + lectureId, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Persists the confidence score for a single GPS reading slot.
     *
     * @param context      Application context.
     * @param lectureId    Unique lecture identifier (used as the SharedPreferences file key).
     * @param readingIndex Slot number (1, 2, or 3).
     * @param score        Confidence score in the range 0–100.
     */
    public static void saveScore(Context context, long lectureId, int readingIndex, int score) {
        getPrefs(context, lectureId).edit()
                .putInt("score_" + readingIndex, score)
                .apply();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the sum of all three confidence scores for the given lecture.
     * Maximum possible value is 300 (three perfect readings), but in practice
     * {@link com.bunkmeter.app.scheduler.EvaluationWorker} thresholds at 80.
     */
    public static int getTotalScore(Context context, long lectureId) {
        SharedPreferences prefs = getPrefs(context, lectureId);
        return prefs.getInt("score_1", 0)
             + prefs.getInt("score_2", 0)
             + prefs.getInt("score_3", 0);
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /** Wipes all readings for the given lecture to prevent SharedPreference leaks. */
    public static void clearReadings(Context context, long lectureId) {
        getPrefs(context, lectureId).edit().clear().apply();
    }
}