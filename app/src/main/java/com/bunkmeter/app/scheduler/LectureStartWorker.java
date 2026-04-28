package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.notifications.AttendanceNotificationHelper;

public class LectureStartWorker extends Worker {

    public LectureStartWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int subjectId = getInputData().getInt("subject_id", -1);
        int startTime = getInputData().getInt("start_time", -1);
        int endTime = getInputData().getInt("end_time", -1);
        String date = getInputData().getString("date");
        int sessionId = getInputData().getInt("session_id", -1);

        if (subjectId == -1 || date == null) return Result.failure();

        // Calculate how long the lecture is in milliseconds
        long durationMillis = (long) (endTime - startTime) * 60 * 1000L;
        if (durationMillis <= 0) durationMillis = 60 * 60 * 1000L; // Safe fallback of 1 hour

        // Trigger the ongoing prompt
        AttendanceNotificationHelper.triggerActiveLectureNotification(
                getApplicationContext(), subjectId, date, startTime, sessionId, durationMillis);

        return Result.success();
    }
}