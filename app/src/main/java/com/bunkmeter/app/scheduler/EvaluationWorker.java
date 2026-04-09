package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.TempReadingStorage;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;

public class EvaluationWorker extends Worker {

    public EvaluationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long lectureId = getInputData().getLong("lecture_id", -1);
        if (lectureId == -1) return Result.failure();

        int validCount = TempReadingStorage.getValidCount(getApplicationContext(), lectureId);

        if (validCount >= 2) {
            // TODO: Majority won! Hook up your AttendanceRepository here to update Room DB
            // AppDatabase.getInstance(getApplicationContext()).attendanceDao().markPresent(lectureId);
        } else {
            // Majority failed (or phone was off). Trigger fallback notification.
            AttendanceNotificationHelper.triggerFallbackNotification(getApplicationContext(), lectureId);
        }

        // Clean up temporary data so we don't leak storage
        TempReadingStorage.clearReadings(getApplicationContext(), lectureId);

        return Result.success();
    }
}