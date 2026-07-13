package com.bunkmeter.app.utils;

import java.util.Objects;

/**
 * Pure, Android-free attendance decision rules.
 *
 * <h3>Why extract this</h3>
 * The rules below used to be copy-pasted inside Android classes
 * ({@code DailySetupWorker}, {@code GeofenceBroadcastReceiver},
 * {@code OngoingLectureWorker}), which made them (a) impossible to unit-test
 * without an emulator and (b) prone to silent drift. The session id is the most
 * dangerous of these: the class that SCHEDULES a lecture's jobs and the class
 * that CANCELS them must compute byte-for-byte the same id, or cancellation
 * quietly fails and a student gets auto-bunked after already being marked.
 * Centralising it here guarantees they agree, and lets us test it on the JVM.
 */
public final class AttendanceLogic {

    private AttendanceLogic() {}

    /**
     * How early (in minutes) before a lecture's start time we still treat the
     * student arriving as "in class". Mirrors the grace window used by the
     * geofence DAO query.
     */
    public static final int EARLY_GRACE_MINUTES = 15;

    /**
     * Deterministic, collision-resistant id for one lecture instance.
     * Used as the WorkManager tag {@code SESSION_<id>} that ties together the
     * cancellable jobs for that lecture. Never returns 0 (WorkManager/Notif IDs
     * of 0 are problematic), and is always non-negative.
     */
    public static int sessionId(int subjectId, String date, int startTime) {
        int id = Math.abs(Objects.hash(subjectId, date, startTime));
        return id == 0 ? 1 : id;
    }

    /** The WorkManager tag form of {@link #sessionId}. */
    public static String sessionTag(int subjectId, String date, int startTime) {
        return "SESSION_" + sessionId(subjectId, date, startTime);
    }

    /**
     * Deterministic id for a lecture's ongoing "Are you in class?" notification.
     * The class that POSTS it ({@code AttendanceNotificationHelper}) and the ones
     * that CANCEL it ({@code HomeViewModel}, {@code GeofenceBroadcastReceiver})
     * must compute byte-for-byte the same id, or the notification lingers after
     * the student already answered. Same drift hazard as {@link #sessionId} —
     * centralised here for the same reason.
     */
    public static int activeNotificationId(int subjectId, String date, int startTime) {
        return Objects.hash("active", subjectId, date, startTime);
    }

    /**
     * True if {@code nowMins} (minutes from midnight) falls inside the window a
     * lecture should be considered "happening now": from {@code EARLY_GRACE_MINUTES}
     * before its start, up to and including its end. Both bounds are inclusive.
     */
    public static boolean isWithinLectureWindow(int startTime, int endTime, int nowMins) {
        return nowMins >= (startTime - EARLY_GRACE_MINUTES) && nowMins <= endTime;
    }
}
