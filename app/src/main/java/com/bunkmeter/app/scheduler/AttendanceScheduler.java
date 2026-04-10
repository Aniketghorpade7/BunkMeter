package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class AttendanceScheduler {

    /**
     * Schedules 3 GPS readings (at 10, 15, and 20 mins into the lecture) followed by an
     * evaluation step (at 22 mins). If 2+ readings are inside the classroom radius, Room DB
     * is updated to PRESENT. Otherwise, a fallback notification fires.
     *
     * All workers are chained so EvaluationWorker only runs after all readings finish.
     */
    public static void scheduleAttendanceCheck(Context context, long lectureId,
                                               int subjectId, String date, int startTime,
                                               double classLat, double classLng, float radiusMeters) {

        WorkManager workManager = WorkManager.getInstance(context);

        // Shared location data for all reading workers
        Data.Builder locationData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putDouble("class_lat", classLat)
                .putDouble("class_lng", classLng)
                .putFloat("radius", radiusMeters);

        // Reading 1 — 10 minutes after lecture starts
        OneTimeWorkRequest reading1 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setInputData(locationData.putInt("reading_index", 1).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Reading 2 — 5 minutes after reading 1 finishes (total ~15m)
        OneTimeWorkRequest reading2 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setInputData(locationData.putInt("reading_index", 2).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Reading 3 — 5 minutes after reading 2 finishes (total ~20m)
        OneTimeWorkRequest reading3 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setInputData(locationData.putInt("reading_index", 3).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Evaluation — runs after all 3 readings; decides PRESENT vs fallback notification
        Data evalData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("subject_id", subjectId)
                .putInt("start_time", startTime)
                .putString("date", date)
                .build();

        OneTimeWorkRequest evaluation = new OneTimeWorkRequest.Builder(EvaluationWorker.class)
                .setInputData(evalData)
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Chain: reading1 → reading2 → reading3 → evaluation
        // (WorkManager runs them sequentially — evaluation only fires after all readings finish)
        workManager.beginWith(reading1)
                .then(reading2)
                .then(reading3)
                .then(evaluation)
                .enqueue();
    }
}