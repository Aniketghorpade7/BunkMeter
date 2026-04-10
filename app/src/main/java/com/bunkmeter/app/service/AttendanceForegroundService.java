package com.bunkmeter.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.bunkmeter.app.R;
import com.bunkmeter.app.receiver.AttendanceActionReceiver;

public class AttendanceForegroundService extends Service {

    public static final String CHANNEL_ID = "OngoingLectureChannel";
    public static final int NOTIFICATION_ID = 1005;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        int subjectId = intent.getIntExtra("subject_id", -1);
        int startTime = intent.getIntExtra("start_time", -1);
        String date = intent.getStringExtra("date");

        if (subjectId == -1 || date == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Action: Present
        Intent presentIntent = new Intent(this, AttendanceActionReceiver.class);
        presentIntent.setAction("ACTION_PRESENT");
        putExtras(presentIntent, subjectId, startTime, date);
        PendingIntent pIntentPresent = PendingIntent.getBroadcast(this, 1, presentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Bunk
        Intent bunkIntent = new Intent(this, AttendanceActionReceiver.class);
        bunkIntent.setAction("ACTION_BUNK");
        putExtras(bunkIntent, subjectId, startTime, date);
        PendingIntent pIntentBunk = PendingIntent.getBroadcast(this, 2, bunkIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action: Cancel
        Intent cancelIntent = new Intent(this, AttendanceActionReceiver.class);
        cancelIntent.setAction("ACTION_CANCEL");
        putExtras(cancelIntent, subjectId, startTime, date);
        PendingIntent pIntentCancel = PendingIntent.getBroadcast(this, 3, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lecture in Progress!")
                .setContentText("Did you attend this lecture? Please mark your status.")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Change to your app icon
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true) // Makes it non-dismissible
                .addAction(android.R.drawable.checkbox_on_background, "Present", pIntentPresent)
                .addAction(android.R.drawable.ic_delete, "Bunk", pIntentBunk)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pIntentCancel)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private void putExtras(Intent intent, int subjectId, int startTime, String date) {
        intent.putExtra("subject_id", subjectId);
        intent.putExtra("start_time", startTime);
        intent.putExtra("date", date);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ongoing Lectures",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}