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

import java.util.Objects;

/**
 * Factory for every notification this app shows related to attendance.
 *
 * <h3>Notification ID strategy — no more magic numbers</h3>
 * All notification IDs and PendingIntent request codes are generated with
 * {@link Objects#hash} so they are:
 * <ul>
 *   <li><strong>Deterministic</strong> — the same inputs always produce the
 *       same ID, making it safe to cancel later.</li>
 *   <li><strong>Collision-resistant</strong> — two different lectures on the
 *       same day produce different IDs, so a second lecture's notification
 *       never overwrites the first one.</li>
 * </ul>
 *
 * <h3>Notification lifecycle</h3>
 * Every action Intent now carries a {@code "notification_id"} extra so
 * {@link AttendanceActionReceiver} can call
 * {@link NotificationManager#cancel(int)} the moment the user taps a button.
 */
public class AttendanceNotificationHelper {

    // -------------------------------------------------------------------------
    // Channel IDs
    // -------------------------------------------------------------------------

    private static final String CHANNEL_FALLBACK   = "attendance_fallback";
    private static final String CHANNEL_CLASSROOM  = "create_classroom_prompt";

    // -------------------------------------------------------------------------
    // Fallback notification
    // -------------------------------------------------------------------------

    /**
     * Fires when GPS confidence was too low to auto-confirm the student's
     * presence.  The student taps YES (→ PRESENT) or NO (→ BUNK).
     *
     * @param lectureId  Unique lecture identifier.
     * @param subjectId  Room DB subject ID.
     * @param date       Date string in {@code "yyyy-MM-dd"} format.
     * @param startTime  Lecture start time in minutes from midnight.
     * @param sessionId  Deterministic session hash — used to cancel this
     *                   exact notification when the user responds.
     */
    public static void triggerFallbackNotification(Context context,
                                                    long lectureId,
                                                    int subjectId,
                                                    String date,
                                                    int startTime,
                                                    int sessionId) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        ensureChannel(nm, CHANNEL_FALLBACK, "Attendance Checks",
                NotificationManager.IMPORTANCE_HIGH);

        // Deterministic notification ID — unique per lecture, safe to cancel later
        int notifId = Objects.hash(lectureId, subjectId, date);

        // --- YES → mark PRESENT ---
        Intent yesIntent = buildActionIntent(context, "ACTION_PRESENT",
                subjectId, date, startTime, notifId, sessionId);
        PendingIntent piYes = PendingIntent.getBroadcast(
                context,
                notifId,          // unique request code
                yesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // --- NO → mark BUNK ---
        Intent noIntent = buildActionIntent(context, "ACTION_BUNK",
                subjectId, date, startTime, notifId, sessionId);
        PendingIntent piNo = PendingIntent.getBroadcast(
                context,
                notifId + 1,      // offset by 1 so it gets its own slot
                noIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_FALLBACK)
                        .setSmallIcon(android.R.drawable.ic_dialog_map)
                        .setContentTitle("Attendance Check")
                        .setContentText(
                                "Unable to confirm your presence automatically. Are you in class?")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .addAction(android.R.drawable.ic_input_add,   "YES (Present)", piYes)
                        .addAction(android.R.drawable.ic_delete,      "NO (Bunk)",     piNo);

        nm.notify(notifId, builder.build());
    }

    // -------------------------------------------------------------------------
    // Classroom setup notification
    // -------------------------------------------------------------------------

    /**
     * One-time prompt asking the user to create a classroom so location-based
     * tracking can work.
     */
    public static void triggerCreateClassroomNotification(Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        ensureChannel(nm, CHANNEL_CLASSROOM, "Classroom Setup Reminders",
                NotificationManager.IMPORTANCE_HIGH);

        // Deterministic, stable ID for this one-time prompt
        int notifId = Objects.hash("classroom_prompt");

        Intent openClassroom = new Intent(context, ClassroomActivity.class);
        openClassroom.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(
                context,
                notifId,
                openClassroom,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_CLASSROOM)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Classroom Not Set Up")
                        .setContentText(
                                "Assign a classroom to enable automatic location-based attendance tracking.")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText("One or more of your subjects doesn't have a classroom "
                                        + "assigned. Tap here to add a classroom so BunkMeter can "
                                        + "automatically track your attendance."))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(piOpen);

        nm.notify(notifId, builder.build());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds an action Intent carrying all the data {@link AttendanceActionReceiver}
     * needs, including the {@code notification_id} so the receiver can dismiss
     * this exact notification from the tray.
     */
    private static Intent buildActionIntent(Context context,
                                             String action,
                                             int subjectId,
                                             String date,
                                             int startTime,
                                             int notifId,
                                             int sessionId) {
        Intent intent = new Intent(context, AttendanceActionReceiver.class);
        intent.setAction(action);
        intent.putExtra("subject_id",      subjectId);
        intent.putExtra("date",            date);
        intent.putExtra("start_time",      startTime);
        intent.putExtra("notification_id", notifId);   // for NotificationManager.cancel()
        intent.putExtra("session_id",      sessionId); // for WorkManager chain cancellation
        return intent;
    }

    /** Creates a notification channel on API 26+. Safe to call multiple times. */
    private static void ensureChannel(NotificationManager nm,
                                       String channelId,
                                       String channelName,
                                       int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(channelId, channelName, importance);
            nm.createNotificationChannel(channel);
        }
    }

    // Add this variable near your other CHANNEL constants at the top
    private static final String CHANNEL_ACTIVE_LECTURE = "active_lecture_channel";

    // Add this new method into the class
    public static void triggerActiveLectureNotification(Context context,
                                                        int subjectId,
                                                        String date,
                                                        int startTime,
                                                        int sessionId,
                                                        long durationMillis) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        ensureChannel(nm, CHANNEL_ACTIVE_LECTURE, "Active Lecture Check-in", NotificationManager.IMPORTANCE_DEFAULT);

        // Unique ID for this specific lecture's active prompt
        int notifId = Objects.hash("active", subjectId, date, startTime);

        // Build the 3 actions
        Intent yesIntent = buildActionIntent(context, "ACTION_PRESENT", subjectId, date, startTime, notifId, sessionId);
        PendingIntent piYes = PendingIntent.getBroadcast(context, notifId, yesIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent noIntent = buildActionIntent(context, "ACTION_BUNK", subjectId, date, startTime, notifId, sessionId);
        PendingIntent piNo = PendingIntent.getBroadcast(context, notifId + 1, noIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent cancelIntent = buildActionIntent(context, "ACTION_CANCEL", subjectId, date, startTime, notifId, sessionId);
        PendingIntent piCancel = PendingIntent.getBroadcast(context, notifId + 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ACTIVE_LECTURE)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("Lecture Time! 🎓")
                .setContentText("Are you in class? (We won't tell if you're sleeping in 🤫)")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Sits quietly in the tray
                .setOngoing(true) // Cannot be easily swiped away
                .setTimeoutAfter(durationMillis) // Magically vanishes when the lecture ends!
                .addAction(android.R.drawable.checkbox_on_background, "Present 🙋‍♂️", piYes)
                .addAction(android.R.drawable.ic_delete, "Bunking 🥷", piNo)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel ❌", piCancel);

        nm.notify(notifId, builder.build());
    }
}