package com.bunkmeter.app.scheduler;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.BackoffPolicy;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.TempReadingStorage;
import com.bunkmeter.app.location.LocationHelper;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager worker that fetches a single GPS reading and converts it into a
 * confidence score that is saved to {@link TempReadingStorage}.
 *
 * <h3>Why {@link ListenableWorker} instead of {@link androidx.work.Worker}?</h3>
 * {@code Worker.doWork()} is synchronous.  Using it with an async API
 * (like {@code FusedLocationProviderClient}) requires either {@code Tasks.await()}
 * (thread-blocking) or a {@code CountDownLatch} (fragile under process death).
 * {@code ListenableWorker} is WorkManager's first-class async contract: work is
 * considered "done" only when the returned {@link ListenableFuture} completes.
 * The OS is free to reclaim the thread in the meantime.
 *
 * <h3>Retry / backoff</h3>
 * When GPS returns {@code null} (hardware not yet warmed up, airplane mode, etc.)
 * the worker returns {@code Result.retry()}.  The {@link BackoffPolicy#EXPONENTIAL}
 * policy set in {@link AttendanceScheduler} will re-schedule with increasing delay
 * (30 s → 60 s → 120 s …) with automatic jitter, letting the sensor spin up
 * without hammering the OS.
 *
 * <h3>Confidence scoring</h3>
 * <pre>
 *  Accuracy ≤  5 m  →  100 pts  (pin-point — can instant-confirm)
 *  Accuracy ≤ 15 m  →   60 pts  (good)
 *  Accuracy ≤ 30 m  →   30 pts  (fair)
 *  Accuracy  > 30 m →   10 pts  (in-radius but noisy signal)
 *  Not in radius / mock →  0 pts
 * </pre>
 */
public class LocationReadingWorker extends ListenableWorker {

    public LocationReadingWorker(@NonNull Context context,
                                 @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        final long   lectureId    = getInputData().getLong("lecture_id",    -1);
        final int    readingIndex = getInputData().getInt("reading_index",  -1);
        final double classLat     = getInputData().getDouble("class_lat",    0);
        final double classLng     = getInputData().getDouble("class_lng",    0);
        final float  radius       = getInputData().getFloat("radius",       20f);

        // Validate required inputs
        if (lectureId == -1 || readingIndex == -1) {
            // Permanent failure — missing configuration, no point retrying
            return CallbackToFutureAdapter.getFuture(completer -> {
                completer.set(Result.failure());
                return "LocationReadingWorker[invalid-input]";
            });
        }

        // Hand the future off to the Fused Location callback
        return CallbackToFutureAdapter.getFuture(completer -> {

            LocationHelper.getFreshLocation(getApplicationContext(), location -> {
                if (location == null) {
                    // GPS unavailable / timed out → ask WorkManager to retry with backoff
                    completer.set(Result.retry());
                    return;
                }

                if (LocationHelper.isMockLocation(location)) {
                    // Fake GPS detected — score 0, don't retry
                    TempReadingStorage.saveScore(getApplicationContext(),
                            lectureId, readingIndex, 0);
                    completer.set(Result.success());
                    return;
                }

                // Compute confidence score
                int score = 0;
                if (LocationHelper.isWithinRadius(location, classLat, classLng, radius)) {
                    score = computeScore(location.getAccuracy());
                }

                TempReadingStorage.saveScore(getApplicationContext(),
                        lectureId, readingIndex, score);
                completer.set(Result.success());
            });

            // Tag returned to WorkManager for debug tracing in logcat
            return "LocationReadingWorker[lecture=" + lectureId
                    + ",reading=" + readingIndex + "]";
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Maps GPS horizontal accuracy (in metres) to a confidence point value.
     *
     * @param accuracyMeters {@link Location#getAccuracy()} value.
     * @return Points in the range [10, 100].
     */
    private static int computeScore(float accuracyMeters) {
        if (accuracyMeters <= 5f)  return 100;  // pin-point
        if (accuracyMeters <= 15f) return 60;   // good
        if (accuracyMeters <= 30f) return 30;   // fair
        return 10;                               // in-radius but noisy
    }
}