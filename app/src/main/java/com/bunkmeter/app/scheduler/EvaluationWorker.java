package com.bunkmeter.app.scheduler;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.TempReadingStorage;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.AttendanceRepository;

public class EvaluationWorker extends Worker {

    public EvaluationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long lectureId = getInputData().getLong("lecture_id", -1);
        int subjectId  = getInputData().getInt("subject_id", -1);
        int startTime  = getInputData().getInt("start_time", -1);
        String date    = getInputData().getString("date");

        if (lectureId == -1 || subjectId == -1 || startTime == -1 || date == null) {
            return Result.failure();
        }

        int validCount = TempReadingStorage.getValidCount(getApplicationContext(), lectureId);

        if (validCount >= 2) {
            // Majority of readings say student IS in classroom — mark PRESENT
            AttendanceRepository repo =
                    new AttendanceRepository((Application) getApplicationContext());
            repo.updateAttendanceStatus(subjectId, date, startTime, 0, Attendance.PRESENT);
        } else {
            // Student not detected (or GPS unavailable) — fire fallback notification
            // so they can confirm manually.
            AttendanceNotificationHelper.triggerFallbackNotification(
                    getApplicationContext(), lectureId, subjectId, date, startTime);
        }

        // Clean up temporary storage to prevent memory leaks
        TempReadingStorage.clearReadings(getApplicationContext(), lectureId);

        return Result.success();
    }
}