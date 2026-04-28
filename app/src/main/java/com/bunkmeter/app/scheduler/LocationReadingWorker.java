package com.bunkmeter.app.scheduler;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.TempReadingStorage;
import com.bunkmeter.app.location.LocationHelper;
import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;

public class LocationReadingWorker extends ListenableWorker {

    private static final int PRESENCE_THRESHOLD = 80;

    public LocationReadingWorker(@NonNull Context context,
                                 @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        final long   lectureId    = getInputData().getLong("lecture_id",    -1);
        final int    readingIndex = getInputData().getInt("reading_index",  -1);
        final int    sessionId    = getInputData().getInt("session_id",     -1);
        final int    subjectId    = getInputData().getInt("subject_id",     -1);
        final String date         = getInputData().getString("date");
        final int    startTime    = getInputData().getInt("start_time",     -1);
        final double classLat     = getInputData().getDouble("class_lat",    0);
        final double classLng     = getInputData().getDouble("class_lng",    0);
        final float  radius       = getInputData().getFloat("radius",       20f);

        if (lectureId == -1 || readingIndex == -1 || subjectId == -1 || date == null) {
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(Result.failure());
                return "LocationReadingWorker[invalid-input]";
            });
        }

        return CallbackToFutureAdapter.getFuture(completer -> {
            LocationHelper.getFreshLocation(getApplicationContext(), location -> {
                if (location == null) {
                    completer.set(Result.retry());
                    return;
                }

                // FIX 2: Stale Data Check (Android Background Location Death Grip)
                long locationAge = System.currentTimeMillis() - location.getTime();
                if (locationAge > 60000) { // Older than 60 seconds is stale data
                    completer.set(Result.retry());
                    return;
                }

                if (LocationHelper.isMockLocation(location)) {
                    processScoreAndEvaluate(0, lectureId, readingIndex, subjectId, date, startTime, sessionId);
                    completer.set(Result.success());
                    return;
                }

                // FIX 3: GPS Bounce Check
                int score = 0;
                float[] results = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(), classLat, classLng, results);
                float distanceInMeters = results[0];

                if (distanceInMeters <= radius) {
                    score = computeScore(location.getAccuracy());
                } else if (distanceInMeters <= 100f) {
                    // Inside bounce zone (outside radius, but within 100m) -> wait for signal to settle
                    completer.set(Result.retry());
                    return;
                } else {
                    // Way outside radius -> Definitely bunking / out of bounds
                    score = 0;
                }

                // FIX 1: Evaluate Inline
                processScoreAndEvaluate(score, lectureId, readingIndex, subjectId, date, startTime, sessionId);
                completer.set(Result.success());
            });

            return "LocationReadingWorker[lecture=" + lectureId + ",reading=" + readingIndex + "]";
        });
    }

    /**
     * Saves the score and immediately evaluates if we have enough points to mark attendance.
     */
    private void processScoreAndEvaluate(int score, long lectureId, int readingIndex,
                                         int subjectId, String date, int startTime, int sessionId) {

        TempReadingStorage.saveScore(getApplicationContext(), lectureId, readingIndex, score);
        int totalScore = TempReadingStorage.getTotalScore(getApplicationContext(), lectureId);

        if (totalScore >= PRESENCE_THRESHOLD) {
            // Success! We crossed the threshold. Auto-mark Present.
            AttendanceRepository repo = new AttendanceRepository((Application) getApplicationContext());
            repo.updateAttendanceStatus(subjectId, date, startTime, 0, AttendanceStatus.PRESENT);

            // OPTIMIZATION: Cancel any remaining readings for this lecture to save battery!
            WorkManager.getInstance(getApplicationContext()).cancelAllWorkByTag("SESSION_" + sessionId);

            // CLEANUP: Cancel the active manual prompt since GPS did the job automatically!
            NotificationManager nm = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                int activeNotifId = Objects.hash("active", subjectId, date, startTime);
                nm.cancel(activeNotifId);
            }

            TempReadingStorage.clearReadings(getApplicationContext(), lectureId);

        } else if (readingIndex == 3) {
            // This is the final reading and we still don't have enough points. Show fallback notification.
            AttendanceNotificationHelper.triggerFallbackNotification(
                    getApplicationContext(), lectureId, subjectId, date, startTime, sessionId);

            TempReadingStorage.clearReadings(getApplicationContext(), lectureId);
        }
    }

    private static int computeScore(float accuracyMeters) {
        if (accuracyMeters <= 5f)  return 100;
        if (accuracyMeters <= 15f) return 60;
        if (accuracyMeters <= 30f) return 30;
        return 10;
    }
}