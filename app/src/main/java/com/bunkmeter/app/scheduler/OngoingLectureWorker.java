package com.bunkmeter.app.scheduler;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.bunkmeter.app.utils.AttendanceLogic;
import com.bunkmeter.app.utils.DateUtils;

/**
 * Mid-lecture reminder. Fires ~30 minutes after a lecture starts.
 *
 * <h3>What changed from the old design</h3>
 * This worker used to launch a non-dismissible {@code AttendanceForegroundService}.
 * That service was removed: with geofencing handling the automatic case and the
 * {@link AutoBunkWorker} guaranteeing the lecture never stays "Pending" forever,
 * a heavyweight foreground service is unnecessary. Now we simply re-show the
 * normal "Are you in class?" notification IF the lecture is still unmarked.
 */
public class OngoingLectureWorker extends Worker {

    public OngoingLectureWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int subjectId = getInputData().getInt("subject_id", -1);
        int startTime = getInputData().getInt("start_time", -1);
        int endTime   = getInputData().getInt("end_time", -1);
        String date   = getInputData().getString("date");

        if (subjectId == -1 || date == null) return Result.failure();

        AttendanceRepository repo = new AttendanceRepository((Application) getApplicationContext());
        Attendance attendance = repo.getSpecificAttendanceSync(subjectId, date, startTime);

        // Only nudge if still undecided. If geofencing or the user already marked
        // it, do nothing.
        if (attendance == null) {
            int sessionId = AttendanceLogic.sessionId(subjectId, date, startTime);

            // Show the prompt for the remainder of the lecture (minimum 5 minutes).
            int nowMins = DateUtils.nowInMinutes();
            long remainingMillis = (long) Math.max(endTime - nowMins, 5) * 60 * 1000L;

            AttendanceNotificationHelper.triggerActiveLectureNotification(
                    getApplicationContext(), subjectId, date, startTime, sessionId, remainingMillis);
        }

        return Result.success();
    }
}
