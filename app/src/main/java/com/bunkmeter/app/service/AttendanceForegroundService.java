package com.bunkmeter.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.bunkmeter.app.R;
import com.bunkmeter.app.receiver.AttendanceActionReceiver;

import java.util.Objects;

public class AttendanceForegroundService extends Service {

    public static final String CHANNEL_ID = "OngoingLectureChannel";

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

        // Deterministic, collision-resistant notification ID for this lecture
        int hash = Objects.hash(subjectId, date, startTime);
        int sessionId = Math.abs(hash);
        if (sessionId == 0) {
            sessionId = 1; // Fallback to prevent Android crash
        }

        // --- Action: Present ---
        PendingIntent pIntentPresent = buildActionPendingIntent(
                "ACTION_PRESENT", subjectId, startTime, date,
                sessionId, sessionId);      // requestCode = sessionId

        // --- Action: Bunk ---
        PendingIntent pIntentBunk = buildActionPendingIntent(
                "ACTION_BUNK", subjectId, startTime, date,
                sessionId, sessionId + 1);  // requestCode = sessionId+1

        // --- Action: Cancel ---
        PendingIntent pIntentCancel = buildActionPendingIntent(
                "ACTION_CANCEL", subjectId, startTime, date,
                sessionId, sessionId + 2);  // requestCode = sessionId+2

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Lecture in Progress!")
                .setContentText("Did you attend this lecture? Please mark your status.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)   // Non-dismissible until the user taps an action
                .addAction(android.R.drawable.checkbox_on_background, "Present", pIntentPresent)
                .addAction(android.R.drawable.ic_delete, "Bunk", pIntentBunk)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pIntentCancel)
                .build();

        // Pass the session-specific ID and Android 14 service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(sessionId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        } else {
            startForeground(sessionId, notification);
        }

        return START_STICKY;
    }

    private PendingIntent buildActionPendingIntent(String action,
                                                   int subjectId,
                                                   int startTime,
                                                   String date,
                                                   int notifId,
                                                   int requestCode) {
        Intent actionIntent = new Intent(this, AttendanceActionReceiver.class);
        actionIntent.setAction(action);
        actionIntent.putExtra("subject_id", subjectId);
        actionIntent.putExtra("start_time", startTime);
        actionIntent.putExtra("date", date);
        actionIntent.putExtra("notification_id", notifId);   // for cancel()
        actionIntent.putExtra("session_id", notifId);   // for future scoped stop

        return PendingIntent.getBroadcast(
                this, requestCode, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ongoing Lectures",
                    NotificationManager.IMPORTANCE_HIGH);
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