package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AttendanceScheduler {

    public static void scheduleAttendanceCheck(Context context,
                                               long lectureId,
                                               int subjectId,
                                               String date,
                                               int startTime,
                                               double classLat,
                                               double classLng,
                                               float radiusMeters) {

        WorkManager workManager = WorkManager.getInstance(context);

        // Unique, deterministic session ID for this lecture
        int hash = Objects.hash(subjectId, date, startTime);
        int sessionId = Math.abs(hash);
        if (sessionId == 0) {
            sessionId = 1; // Fallback to prevent crash
        }

        // We now pass ALL required data so the Location worker can evaluate the DB itself
        Data baseData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("session_id", sessionId)
                .putInt("subject_id", subjectId)
                .putString("date", date)
                .putInt("start_time", startTime)
                .putDouble("class_lat", classLat)
                .putDouble("class_lng", classLng)
                .putFloat("radius", radiusMeters)
                .build();

        // Used to cancel the 2nd/3rd reading if the 1st reading succeeds early!
        String sessionTag = "SESSION_" + sessionId;

        OneTimeWorkRequest reading1 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 1).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .addTag(sessionTag)
                .build();

        OneTimeWorkRequest reading2 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 2).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .addTag(sessionTag)
                .build();

        OneTimeWorkRequest reading3 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 3).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .addTag(sessionTag)
                .build();

        // Enqueue only the readings. Evaluation happens dynamically inside them!
        workManager.enqueue(Arrays.asList(reading1, reading2, reading3));
    }
}