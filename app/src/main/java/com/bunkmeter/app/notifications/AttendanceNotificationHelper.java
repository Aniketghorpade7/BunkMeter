package com.bunkmeter.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.bunkmeter.app.R;

public class AttendanceNotificationHelper {

    private static final String CHANNEL_ID = "attendance_fallback";

    public static void triggerFallbackNotification(Context context, long lectureId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Attendance Checks", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // Placeholder intents - these will later point to a BroadcastReceiver to update Room DB
        PendingIntent yesIntent = PendingIntent.getBroadcast(context, (int) lectureId, new Intent(), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent noIntent = PendingIntent.getBroadcast(context, (int) lectureId + 1, new Intent(), PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map) // Update with your actual R.drawable icon later
                .setContentTitle("Attendance Check")
                .setContentText("Unable to confirm your presence automatically. Are you in class?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_input_add, "YES (Present)", yesIntent)
                .addAction(android.R.drawable.ic_delete, "NO (Bunk)", noIntent);

        notificationManager.notify((int) lectureId, builder.build());
    }
}