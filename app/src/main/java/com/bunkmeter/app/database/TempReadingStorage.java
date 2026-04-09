package com.bunkmeter.app.database;

import android.content.Context;
import android.content.SharedPreferences;

public class TempReadingStorage {

    private static final String PREF_PREFIX = "attendance_temp_";

    private static SharedPreferences getPrefs(Context context, long lectureId) {
        return context.getSharedPreferences(PREF_PREFIX + lectureId, Context.MODE_PRIVATE);
    }

    public static void saveReading(Context context, long lectureId, int readingIndex, boolean isValid) {
        getPrefs(context, lectureId).edit()
                .putBoolean("reading_" + readingIndex, isValid)
                .apply();
    }

    public static int getValidCount(Context context, long lectureId) {
        SharedPreferences prefs = getPrefs(context, lectureId);
        int validCount = 0;
        if (prefs.getBoolean("reading_1", false)) validCount++;
        if (prefs.getBoolean("reading_2", false)) validCount++;
        if (prefs.getBoolean("reading_3", false)) validCount++;
        return validCount;
    }

    public static void clearReadings(Context context, long lectureId) {
        getPrefs(context, lectureId).edit().clear().apply();
    }
}