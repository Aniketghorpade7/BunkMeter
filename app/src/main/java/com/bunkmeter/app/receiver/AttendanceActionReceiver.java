package com.bunkmeter.app.receiver;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.WorkManager;

import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.repository.AttendanceRepository;

public class AttendanceActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        // --- Extract payload ---
        int    subjectId      = intent.getIntExtra("subject_id",      -1);
        int    startTime      = intent.getIntExtra("start_time",      -1);
        String date           = intent.getStringExtra("date");
        int    notificationId = intent.getIntExtra("notification_id", -1);
        int    sessionId      = intent.getIntExtra("session_id",      -1); // Needed for battery saver

        if (subjectId == -1 || date == null) return;

        // --- Dismiss the notification immediately ---
        if (notificationId != -1) {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(notificationId);
            }
        }

        // --- Persist the attendance status using typed enum constants ---
        Application app = (Application) context.getApplicationContext();
        AttendanceRepository repository = new AttendanceRepository(app);

        switch (intent.getAction()) {
            case "ACTION_PRESENT":
                repository.updateAttendanceStatus(
                        subjectId, date, startTime, 0, AttendanceStatus.PRESENT);
                break;

            case "ACTION_BUNK":
                repository.updateAttendanceStatus(
                        subjectId, date, startTime, 0, AttendanceStatus.BUNK);
                break;

            case "ACTION_CANCEL":
                repository.updateAttendanceStatus(
                        subjectId, date, startTime, 0, AttendanceStatus.CANCELLED);
                break;

            default:
                return;
        }

        // --- Cancel pending jobs for THIS lecture only ---
        // This tag covers the OngoingLectureWorker reminder and the AutoBunkWorker
        // safety net, so a manual decision stops them from firing later.
        if (sessionId != -1) {
            WorkManager.getInstance(context).cancelAllWorkByTag("SESSION_" + sessionId);
        }
    }
}