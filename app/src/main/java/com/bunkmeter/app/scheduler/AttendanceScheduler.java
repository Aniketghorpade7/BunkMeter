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

        // Shared location base data
        Data baseData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("session_id", sessionId)
                .putDouble("class_lat", classLat)
                .putDouble("class_lng", classLng)
                .putFloat("radius", radiusMeters)
                .build();

        // Schedule independently using parallel absolute delays
        OneTimeWorkRequest reading1 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 1).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        OneTimeWorkRequest reading2 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 2).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        OneTimeWorkRequest reading3 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(new Data.Builder().putAll(baseData).putInt("reading_index", 3).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Evaluation - Runs independently at T+22 minutes
        Data evalData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("subject_id", subjectId)
                .putInt("start_time", startTime)
                .putInt("session_id", sessionId)
                .putString("date", date)
                .build();

        OneTimeWorkRequest evaluation = new OneTimeWorkRequest.Builder(EvaluationWorker.class)
                .setInitialDelay(22, TimeUnit.MINUTES)
                .setInputData(evalData)
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Enqueue them all independently so retries don't cascade delays
        workManager.enqueue(Arrays.asList(reading1, reading2, reading3, evaluation));
    }
}