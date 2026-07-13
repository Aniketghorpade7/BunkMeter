package com.bunkmeter.app;

import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.utils.AttendanceLogic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented test for the active-lecture notification DISMISSAL CONTRACT: the id
 * used to POST the "Are you in class?" notification must equal the id used to
 * CANCEL it, and two lectures must get different ids (so marking one doesn't
 * dismiss the other).
 *
 * Runs against the real {@code NotificationManager} and inspects
 * {@code getActiveNotifications()} — this is the end-to-end proof that the
 * de-duplicated {@code AttendanceLogic.activeNotificationId} keeps post and cancel
 * in agreement across {@code AttendanceNotificationHelper}, {@code HomeViewModel},
 * and {@code GeofenceBroadcastReceiver}.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationContractTest {

    private static final String DATE = "2026-07-13";
    private static final long HOUR = 60L * 60L * 1000L;

    private final Context ctx = ApplicationProvider.getApplicationContext();
    private final NotificationManager nm =
            (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);

    @Before
    public void grantNotifPermissionAndClear() {
        // POST_NOTIFICATIONS is a runtime permission on API 33+. It is declared in
        // the app manifest, so the instrumentation can grant it programmatically.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(ctx.getPackageName(),
                            "android.permission.POST_NOTIFICATIONS");
        }
        nm.cancelAll();
    }

    @After
    public void tearDown() {
        nm.cancelAll();
    }

    @Test
    public void postedNotificationCancelsBySameId() throws Exception {
        int subjectId = 7, startTime = 570;
        int id = AttendanceLogic.activeNotificationId(subjectId, DATE, startTime);

        AttendanceNotificationHelper.triggerActiveLectureNotification(
                ctx, subjectId, DATE, startTime, /* sessionId */ 12345, HOUR);
        assertTrue("notification should be visible after posting", waitUntil(id, true));

        // HomeViewModel / GeofenceBroadcastReceiver cancel using this SAME id.
        nm.cancel(id);
        assertTrue("notification should be gone after cancel by the shared id", waitUntil(id, false));
    }

    @Test
    public void differentLecturesDoNotCollide() throws Exception {
        int idEarly = AttendanceLogic.activeNotificationId(7, DATE, 570);
        int idLate = AttendanceLogic.activeNotificationId(7, DATE, 720);

        AttendanceNotificationHelper.triggerActiveLectureNotification(ctx, 7, DATE, 570, 1, HOUR);
        AttendanceNotificationHelper.triggerActiveLectureNotification(ctx, 7, DATE, 720, 2, HOUR);
        assertTrue(waitUntil(idEarly, true));
        assertTrue(waitUntil(idLate, true));

        // Cancelling the early lecture must NOT dismiss the late one.
        nm.cancel(idEarly);
        assertTrue(waitUntil(idEarly, false));
        assertTrue("cancelling one lecture must leave the other's prompt up", isActive(idLate));
    }

    private boolean isActive(int id) {
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (sbn.getId() == id) return true;
        }
        return false;
    }

    private boolean waitUntil(int id, boolean present) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (isActive(id) == present) return true;
            Thread.sleep(20);
        }
        return false;
    }
}
