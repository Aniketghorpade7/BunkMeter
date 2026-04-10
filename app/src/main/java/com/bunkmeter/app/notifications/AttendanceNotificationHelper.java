package com.bunkmeter.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.bunkmeter.app.R;
import com.bunkmeter.app.receiver.AttendanceActionReceiver;
import com.bunkmeter.app.ui.settings.ClassroomActivity;

public class AttendanceNotificationHelper {

    private static final String CHANNEL_ID = "attendance_fallback";

    /**
     * Fires a fallback notification when GPS could not confirm the student's presence.
     * Tapping YES marks PRESENT; tapping NO marks BUNK — both actions go to AttendanceActionReceiver.
     *
     * @param lectureId  unique identifier for the lecture (used as notification ID)
     * @param subjectId  Room DB subject ID
     * @param date       date string in "yyyy-MM-dd" format
     * @param startTime  lecture start time in minutes from midnight
     */
    public static void triggerFallbackNotification(Context context, long lectureId,
                                                   int subjectId, String date, int startTime) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Attendance Checks", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        // YES → mark PRESENT
        Intent yesIntentRaw = new Intent(context, AttendanceActionReceiver.class);
        yesIntentRaw.setAction("ACTION_PRESENT");
        yesIntentRaw.putExtra("subject_id", subjectId);
        yesIntentRaw.putExtra("date", date);
        yesIntentRaw.putExtra("start_time", startTime);
        PendingIntent yesIntent = PendingIntent.getBroadcast(
                context, (int) lectureId, yesIntentRaw,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // NO → mark BUNK
        Intent noIntentRaw = new Intent(context, AttendanceActionReceiver.class);
        noIntentRaw.setAction("ACTION_BUNK");
        noIntentRaw.putExtra("subject_id", subjectId);
        noIntentRaw.putExtra("date", date);
        noIntentRaw.putExtra("start_time", startTime);
        PendingIntent noIntent = PendingIntent.getBroadcast(
                context, (int) lectureId + 1, noIntentRaw,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Attendance Check")
                .setContentText("Unable to confirm your presence automatically. Are you in class?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_input_add, "YES (Present)", yesIntent)
                .addAction(android.R.drawable.ic_delete, "NO (Bunk)", noIntent);

        notificationManager.notify((int) lectureId, builder.build());
    }

    private static final String CLASSROOM_CHANNEL_ID = "create_classroom_prompt";
    private static final int CLASSROOM_NOTIFICATION_ID = 2001;

    /**
     * Fires a one-time notification prompting the user to create a classroom.
     * Tapping it opens ClassroomActivity where they can add one.
     */
    public static void triggerCreateClassroomNotification(Context context) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CLASSROOM_CHANNEL_ID,
                    "Classroom Setup Reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Reminds you to add a classroom for location-based attendance");
            notificationManager.createNotificationChannel(channel);
        }

        // Deep-link to ClassroomActivity so the user can add a classroom immediately
        Intent openClassroom = new Intent(context, ClassroomActivity.class);
        openClassroom.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, CLASSROOM_NOTIFICATION_ID, openClassroom,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CLASSROOM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Classroom Not Set Up")
                .setContentText("Assign a classroom to enable automatic location-based attendance tracking.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("One or more of your subjects doesn't have a classroom assigned. " +
                                "Tap here to add a classroom so BunkMeter can automatically track your attendance."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(CLASSROOM_NOTIFICATION_ID, builder.build());
    }
}