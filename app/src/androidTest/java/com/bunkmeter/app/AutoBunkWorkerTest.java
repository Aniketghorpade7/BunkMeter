package com.bunkmeter.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.scheduler.AutoBunkWorker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Instrumented tests for the auto-bunk safety net. {@code AutoBunkWorker} must
 * record a BUNK for a lecture still Pending at its end time, and must LEAVE ALONE
 * a lecture already decided (by geofencing or a manual tap).
 *
 * Uses work-testing's {@code TestWorkerBuilder} to run the worker synchronously,
 * plus an injected in-memory DB (reflection; no production seam).
 */
@RunWith(AndroidJUnit4.class)
public class AutoBunkWorkerTest {

    private static final String DATE = "2026-07-13";
    private static final int START = 570;
    private static final int END = 630;

    private AppDatabase db;
    private Context appCtx;
    private Executor exec;
    private int subjectId;

    @Before
    public void setup() {
        appCtx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(appCtx, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabaseTestAccess.inject(db);
        exec = Executors.newSingleThreadExecutor();
        subjectId = (int) db.subjectDao().insertSubject(new Subject("Math", "#FF0000"));
    }

    @After
    public void tearDown() {
        AppDatabaseTestAccess.reset();
        db.close();
    }

    private ListenableWorker.Result runAutoBunk() {
        AutoBunkWorker worker = TestWorkerBuilder.from(appCtx, AutoBunkWorker.class, exec)
                .setInputData(new Data.Builder()
                        .putInt("subject_id", subjectId)
                        .putInt("start_time", START)
                        .putInt("end_time", END)
                        .putString("date", DATE)
                        .build())
                .build();
        return worker.doWork();
    }

    @Test
    public void pendingLectureGetsBunked() throws Exception {
        ListenableWorker.Result result = runAutoBunk();
        assertEquals(ListenableWorker.Result.success(), result);

        Attendance a = await();
        assertNotNull("safety net must create a row when still pending", a);
        assertEquals(AttendanceStatus.BUNK, a.getAttendanceStatus());
        assertEquals(END, a.getEndTime());   // endTime carried through, not 0
    }

    @Test
    public void alreadyDecidedLectureIsLeftAlone() throws Exception {
        // Simulate geofencing having already marked PRESENT.
        Attendance present = new Attendance();
        present.setSubjectId(subjectId);
        present.setDate(DATE);
        present.setStartTime(START);
        present.setEndTime(END);
        present.setStatus(AttendanceStatus.PRESENT.value);
        db.attendanceDao().insertAttendance(present);

        runAutoBunk();
        Thread.sleep(300); // give any (erroneous) async write a chance to land

        Attendance a = db.attendanceDao().getSpecificAttendance(subjectId, DATE, START);
        assertNotNull(a);
        assertEquals("must not overwrite a decided lecture",
                AttendanceStatus.PRESENT, a.getAttendanceStatus());
        assertEquals(1, db.attendanceDao().getAttendanceBySubject(subjectId).size());
    }

    private Attendance await() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Attendance a = db.attendanceDao().getSpecificAttendance(subjectId, DATE, START);
            if (a != null) return a;
            Thread.sleep(10);
        }
        return null;
    }
}
