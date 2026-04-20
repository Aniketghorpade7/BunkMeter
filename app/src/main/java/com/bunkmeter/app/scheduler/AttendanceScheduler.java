package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the GPS reading chain and the evaluation step for a single lecture.
 *
 * <h3>Session ID</h3>
 * A deterministic {@code sessionId} is computed from
 * {@code (subjectId, date, startTime)} using {@link Objects#hash}.  It is
 * threaded through every worker's input data so EvaluationWorker can pass it
 * to the notification helper, which uses it both as the notification ID and as
 * the key for {@code NotificationManager.cancel()}.
 *
 * <h3>Exponential backoff</h3>
 * Each {@link LocationReadingWorker} request carries
 * {@link BackoffPolicy#EXPONENTIAL} with a 30-second initial delay.  When GPS
 * is not ready the worker returns {@code Result.retry()} and WorkManager
 * automatically doubles the delay (30 s → 60 s → 120 s …) with jitter, giving
 * the hardware time to warm up without hammering the OS scheduler.
 */
public class AttendanceScheduler {

    /**
     * Schedules 3 GPS readings (at 10, 15, and 20 mins into the lecture)
     * followed by an evaluation step (at ~22 mins).
     *
     * All workers are chained so {@link EvaluationWorker} only runs after all
     * readings have finished (or exhausted their retry budget).
     */
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
        int sessionId = Objects.hash(subjectId, date, startTime);

        // Shared location data injected into every reading worker
        Data.Builder locationDataBase = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("session_id",  sessionId)
                .putDouble("class_lat", classLat)
                .putDouble("class_lng", classLng)
                .putFloat("radius",     radiusMeters);

        // ----------------------------------------------------------------
        // Reading 1 — 10 minutes after lecture starts
        // ----------------------------------------------------------------
        OneTimeWorkRequest reading1 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(locationDataBase.putInt("reading_index", 1).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // ----------------------------------------------------------------
        // Reading 2 — 5 more minutes later (~15 min since lecture start)
        // ----------------------------------------------------------------
        OneTimeWorkRequest reading2 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(locationDataBase.putInt("reading_index", 2).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // ----------------------------------------------------------------
        // Reading 3 — 5 more minutes later (~20 min since lecture start)
        // ----------------------------------------------------------------
        OneTimeWorkRequest reading3 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(locationDataBase.putInt("reading_index", 3).build())
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // ----------------------------------------------------------------
        // Evaluation — runs after all 3 readings finish
        // ----------------------------------------------------------------
        Data evalData = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putInt("subject_id",  subjectId)
                .putInt("start_time",  startTime)
                .putInt("session_id",  sessionId)
                .putString("date",     date)
                .build();

        OneTimeWorkRequest evaluation = new OneTimeWorkRequest.Builder(EvaluationWorker.class)
                .setInputData(evalData)
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        // Chain: reading1 → reading2 → reading3 → evaluation
        workManager.beginWith(reading1)
                .then(reading2)
                .then(reading3)
                .then(evaluation)
                .enqueue();
    }
}