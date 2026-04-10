package com.bunkmeter.app.scheduler;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.bunkmeter.app.service.AttendanceForegroundService;

public class OngoingLectureWorker extends Worker {

    public OngoingLectureWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int subjectId = getInputData().getInt("subject_id", -1);
        int startTime = getInputData().getInt("start_time", -1);
        String date = getInputData().getString("date");

        if (subjectId == -1 || date == null) return Result.failure();

        AttendanceRepository repo = new AttendanceRepository((android.app.Application) getApplicationContext());
        Attendance attendance = repo.getSpecificAttendanceSync(subjectId, date, startTime);

        // If attendance doesn't exist, it means auto-location failed and the user hasn't marked it manually
        if (attendance == null) {
            Intent serviceIntent = new Intent(getApplicationContext(), AttendanceForegroundService.class);
            serviceIntent.putExtra("subject_id", subjectId);
            serviceIntent.putExtra("start_time", startTime);
            serviceIntent.putExtra("date", date);

            // Start the non-dismissible Foreground Service
            ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
        }

        return Result.success();
    }
}