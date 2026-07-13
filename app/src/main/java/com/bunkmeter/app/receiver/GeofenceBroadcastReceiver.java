package com.bunkmeter.app.receiver;

import android.app.Application;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.work.WorkManager;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.database.TimetableDao;
import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.bunkmeter.app.utils.AttendanceLogic;
import com.bunkmeter.app.utils.DateUtils;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.util.List;

/**
 * Fires when the OS detects the user entered one of our classroom geofences.
 *
 * <h3>The whole automatic-attendance decision lives here</h3>
 * Entering a geofence is NOT enough on its own to mark attendance — the student
 * might walk through a building at 8 PM. So when an ENTER event arrives we ask:
 * "Is there a lecture scheduled in THIS room RIGHT NOW?" Only then do we mark
 * the student PRESENT.
 *
 * <h3>goAsync()</h3>
 * A BroadcastReceiver's {@code onReceive} runs on the main thread and must
 * return quickly. We need to touch the database (disk I/O), so we call
 * {@link #goAsync()} to tell Android "keep me alive a bit longer", do the work
 * on a background executor, then call {@code finish()}.
 */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";
    public static final String ACTION_GEOFENCE_EVENT =
            "com.bunkmeter.app.ACTION_GEOFENCE_EVENT";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1) Turn the raw intent into a typed geofence event.
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null || event.hasError()) {
            Log.e(TAG, "Geofence event error: "
                    + (event != null ? event.getErrorCode() : "null event"));
            return;
        }

        // 2) We only registered for ENTER, but it's good practice to verify.
        if (event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_ENTER) {
            return;
        }

        List<Geofence> triggering = event.getTriggeringGeofences();
        if (triggering == null || triggering.isEmpty()) return;

        // 3) Hand off to a background thread so onReceive returns fast.
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                processEntries(appContext, triggering);
            } finally {
                pending.finish(); // ALWAYS finish, even if something throws.
            }
        });
    }

    private void processEntries(Context appContext, List<Geofence> triggering) {
        int appDay = DateUtils.todayAppDay();
        if (appDay == DateUtils.SUNDAY_NO_LECTURES) return; // no lectures Sunday

        int nowMins = DateUtils.nowInMinutes();
        String today = DateUtils.todayDateString();

        AppDatabase db = AppDatabase.getInstance(appContext);
        TimetableDao timetableDao = db.timetableDao();
        AttendanceRepository repo = new AttendanceRepository((Application) appContext);

        for (Geofence g : triggering) {
            // The request ID we set during registration was the classroom's PK.
            int classroomId;
            try {
                classroomId = Integer.parseInt(g.getRequestId());
            } catch (NumberFormatException e) {
                continue;
            }

            // Is a lecture happening in THIS room right now?
            List<Timetable> activeLectures =
                    timetableDao.getActiveLecturesForClassroomSync(appDay, classroomId, nowMins);

            for (Timetable lecture : activeLectures) {
                int subjectId = lecture.getSubjectId();
                int startTime = lecture.getStartTime();
                int endTime   = lecture.getEndTime();

                // Defensive double-check of the time window using the same shared
                // rule the tests cover (the SQL already filters, but this keeps the
                // grace-window constant single-sourced).
                if (!AttendanceLogic.isWithinLectureWindow(startTime, endTime, nowMins)) {
                    continue;
                }

                // Don't overwrite a status the student already set manually.
                if (repo.getSpecificAttendanceSync(subjectId, today, startTime) != null) {
                    continue;
                }

                // Auto-mark PRESENT (endTime is carried through so the row is complete).
                repo.updateAttendanceStatus(
                        subjectId, today, startTime, endTime, classroomId, AttendanceStatus.PRESENT);

                Log.d(TAG, "Auto-marked PRESENT: subject=" + subjectId + " room=" + classroomId);

                cancelPendingPromptsFor(appContext, subjectId, today, startTime);
            }
        }
    }

    /**
     * Once geofencing has confirmed presence, the manual prompt and the pending
     * auto-bunk job are no longer needed — clean them up. The IDs/tags here MUST
     * match the ones used when the prompt and worker were created.
     */
    private void cancelPendingPromptsFor(Context context, int subjectId, String date, int startTime) {
        // Cancel the "Are you in class?" active-lecture notification.
        int activeNotifId = AttendanceLogic.activeNotificationId(subjectId, date, startTime);
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(activeNotifId);

        // Cancel the scheduled AutoBunkWorker + OngoingLecture reminder for this
        // lecture. The tag MUST match what DailySetupWorker used to schedule them.
        WorkManager.getInstance(context).cancelAllWorkByTag(
                AttendanceLogic.sessionTag(subjectId, date, startTime));
    }
}
