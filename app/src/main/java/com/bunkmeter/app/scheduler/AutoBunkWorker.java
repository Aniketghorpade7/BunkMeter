package com.bunkmeter.app.scheduler;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.repository.AttendanceRepository;

/**
 * The final safety net of the attendance flow.
 *
 * <h3>Why we need it</h3>
 * Geofencing handles the "student showed up" case automatically. The manual
 * notification handles the "student taps a button" case. But what if NEITHER
 * happens — geofencing didn't trigger (permission denied / phone left at home)
 * and the student ignored the notification? Without this worker the lecture
 * would sit as "Pending" forever, silently skewing stats.
 *
 * So this worker runs late in the lecture and, if the status is STILL unmarked,
 * records a BUNK. The accompanying notification offers an "Undo" so an honest
 * student who simply ignored the prompt can fix it with one tap.
 *
 * <h3>How it gets cancelled</h3>
 * It is enqueued with the tag {@code "SESSION_<sessionId>"}. The instant the
 * student is marked — by geofencing ({@link GeofenceBroadcastReceiver}) or by a
 * notification button ({@code AttendanceActionReceiver}) — that tag is
 * cancelled, so this worker never runs for an already-decided lecture.
 */
public class AutoBunkWorker extends Worker {

    public AutoBunkWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
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

        // Only act if the lecture is still undecided. If geofencing or the user
        // already set a status, getSpecificAttendanceSync returns non-null and we
        // leave it alone.
        Attendance existing = repo.getSpecificAttendanceSync(subjectId, date, startTime);
        if (existing == null) {
            repo.updateAttendanceStatus(
                    subjectId, date, startTime, endTime, 0, AttendanceStatus.BUNK);
            // (A notification with an "Undo" action can be shown here later;
            //  kept minimal for now so the data stays correct.)
        }

        return Result.success();
    }
}
