package com.bunkmeter.app.receiver;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.bunkmeter.app.service.AttendanceForegroundService;

public class AttendanceActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        int subjectId = intent.getIntExtra("subject_id", -1);
        int startTime = intent.getIntExtra("start_time", -1);
        String date = intent.getStringExtra("date");

        if (subjectId == -1 || date == null) return;

        // Ensure we pass the Application context to the Repository
        Application app = (Application) context.getApplicationContext();
        AttendanceRepository repository = new AttendanceRepository(app);

        switch (intent.getAction()) {
            case "ACTION_PRESENT":
                repository.updateAttendanceStatus(subjectId, date, startTime, 0, 1);
                break;
            case "ACTION_BUNK":
                repository.updateAttendanceStatus(subjectId, date, startTime, 0, 0);
                break;
            case "ACTION_CANCEL":
                repository.updateAttendanceStatus(subjectId, date, startTime, 0, 2); // 2 = Cancelled
                break;
        }

        // Kill the foreground service now that the user has interacted
        Intent stopServiceIntent = new Intent(context, AttendanceForegroundService.class);
        context.stopService(stopServiceIntent);
    }
}