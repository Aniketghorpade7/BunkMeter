package com.bunkmeter.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.bunkmeter.app.utils.AttendanceLogic;

import org.junit.Test;

/**
 * Host-side unit tests for {@link AttendanceLogic} — the shared, Android-free
 * decision rules used by the scheduler, the geofence receiver, and the workers.
 */
public class AttendanceLogicTest {

    // ---- sessionId ----------------------------------------------------------

    @Test
    public void sessionId_isDeterministic() {
        // Same inputs must ALWAYS give the same id — otherwise the class that
        // schedules a lecture's jobs and the class that cancels them disagree,
        // and cancellation silently fails.
        int a = AttendanceLogic.sessionId(7, "2026-06-25", 570);
        int b = AttendanceLogic.sessionId(7, "2026-06-25", 570);
        assertEquals(a, b);
    }

    @Test
    public void sessionId_differsForDifferentLectures() {
        int morning = AttendanceLogic.sessionId(7, "2026-06-25", 570);
        int noon    = AttendanceLogic.sessionId(7, "2026-06-25", 720);
        int other   = AttendanceLogic.sessionId(8, "2026-06-25", 570);
        assertNotEquals(morning, noon);
        assertNotEquals(morning, other);
    }

    @Test
    public void sessionId_isNeverZeroAndNonNegative() {
        // 0 is a problematic WorkManager/notification id, and a negative id can't
        // be used. The helper guarantees a positive value.
        for (int s = 0; s < 50; s++) {
            for (int t = 0; t < 1440; t += 30) {
                int id = AttendanceLogic.sessionId(s, "2026-06-25", t);
                assertTrue("id must be >= 1", id >= 1);
            }
        }
    }

    @Test
    public void sessionTag_isSessionPrefixPlusId() {
        int id = AttendanceLogic.sessionId(7, "2026-06-25", 570);
        assertEquals("SESSION_" + id, AttendanceLogic.sessionTag(7, "2026-06-25", 570));
    }

    // ---- active-lecture notification id -------------------------------------

    @Test
    public void activeNotificationId_isDeterministic() {
        // The class that POSTS the "Are you in class?" notification and the ones
        // that CANCEL it (HomeViewModel on a manual mark, GeofenceBroadcastReceiver
        // on an auto-mark) must derive the SAME id, or the ongoing notification
        // lingers after the student already answered. Both routes go through this
        // one method, so testing its determinism is the dismissal contract.
        int posted    = AttendanceLogic.activeNotificationId(7, "2026-06-25", 570);
        int cancelled = AttendanceLogic.activeNotificationId(7, "2026-06-25", 570);
        assertEquals(posted, cancelled);
    }

    @Test
    public void activeNotificationId_differsForDifferentLectures() {
        // Two lectures must not share a notification id, or marking one would
        // dismiss the other's prompt (or a second lecture would overwrite it).
        int morning = AttendanceLogic.activeNotificationId(7, "2026-06-25", 570);
        int noon    = AttendanceLogic.activeNotificationId(7, "2026-06-25", 720);
        int other   = AttendanceLogic.activeNotificationId(8, "2026-06-25", 570);
        int nextDay = AttendanceLogic.activeNotificationId(7, "2026-06-26", 570);
        assertNotEquals(morning, noon);
        assertNotEquals(morning, other);
        assertNotEquals(morning, nextDay);
    }

    // ---- lecture time window ------------------------------------------------

    @Test
    public void window_includesGraceBeforeStart() {
        // Lecture 09:30(570)–10:30(630). Grace = 15 min, so 09:15 (555) counts.
        assertTrue(AttendanceLogic.isWithinLectureWindow(570, 630, 555)); // exactly start-15
        assertTrue(AttendanceLogic.isWithinLectureWindow(570, 630, 560)); // a bit before start
    }

    @Test
    public void window_includesStartAndEndInclusive() {
        assertTrue(AttendanceLogic.isWithinLectureWindow(570, 630, 570)); // start
        assertTrue(AttendanceLogic.isWithinLectureWindow(570, 630, 600)); // middle
        assertTrue(AttendanceLogic.isWithinLectureWindow(570, 630, 630)); // end inclusive
    }

    @Test
    public void window_excludesTooEarlyAndAfterEnd() {
        assertFalse(AttendanceLogic.isWithinLectureWindow(570, 630, 554)); // 16 min early
        assertFalse(AttendanceLogic.isWithinLectureWindow(570, 630, 360)); // hours early
        assertFalse(AttendanceLogic.isWithinLectureWindow(570, 630, 631)); // 1 min after end
    }
}
