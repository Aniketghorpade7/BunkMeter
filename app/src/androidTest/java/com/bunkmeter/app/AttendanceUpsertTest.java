package com.bunkmeter.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.AttendanceStatus;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.repository.AttendanceRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Instrumented tests for {@code AttendanceRepository}'s upsert, focused on the
 * endTime fix (bug #2): a newly created row must never store {@code endTime = 0}.
 *
 * The repository resolves its DB through {@code AppDatabase.getInstance()}, so we
 * inject an in-memory database via reflection ({@link AppDatabaseTestAccess}) — no
 * production seam, and the real {@code bunkmeter_db} is never touched.
 */
@RunWith(AndroidJUnit4.class)
public class AttendanceUpsertTest {

    private static final String DATE = "2026-07-13"; // a Monday
    private static final int MON = 0;
    private static final int START = 570;
    private static final int END = 630;

    private AppDatabase db;
    private AttendanceRepository repo;
    private int subjectId;
    private int roomId;

    @Before
    public void setup() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        AppDatabaseTestAccess.inject(db);

        subjectId = (int) db.subjectDao().insertSubject(new Subject("Math", "#FF0000"));
        Classroom c = new Classroom();
        c.setName("Room A");
        c.setRadius(100);
        db.classroomDao().insert(c);
        roomId = db.classroomDao().getActiveClassrooms().get(0).getClassroomId();

        repo = new AttendanceRepository((Application) ctx);
    }

    @After
    public void tearDown() {
        AppDatabaseTestAccess.reset();
        db.close();
    }

    @Test
    public void newRowCarriesExplicitEndTime() throws Exception {
        repo.updateAttendanceStatus(subjectId, DATE, START, END, roomId, AttendanceStatus.PRESENT);
        Attendance a = await();
        assertNotNull(a);
        assertEquals(END, a.getEndTime());       // NOT 0 (bug #2)
        assertEquals(AttendanceStatus.PRESENT, a.getAttendanceStatus());
        assertEquals(roomId, (int) a.getClassroomId());
    }

    @Test
    public void newRowLooksUpEndTimeFromTimetable() throws Exception {
        // Legacy 5-arg overload passes no endTime → repo must recover it from the
        // Timetable row for (subject, mapped-day, startTime).
        db.timetableDao().insertTimetable(new Timetable(subjectId, MON, START, END, roomId, "Lecture"));
        repo.updateAttendanceStatus(subjectId, DATE, START, roomId, AttendanceStatus.PRESENT.value);
        Attendance a = await();
        assertNotNull(a);
        assertEquals(END, a.getEndTime());       // recovered from Timetable, not 0
    }

    @Test
    public void secondCallUpdatesInsteadOfDuplicating() throws Exception {
        repo.updateAttendanceStatus(subjectId, DATE, START, END, roomId, AttendanceStatus.PRESENT);
        awaitStatus(AttendanceStatus.PRESENT.value);   // let the insert land first
        repo.updateAttendanceStatus(subjectId, DATE, START, END, roomId, AttendanceStatus.BUNK);
        awaitStatus(AttendanceStatus.BUNK.value);

        List<Attendance> all = db.attendanceDao().getAttendanceBySubject(subjectId);
        assertEquals(1, all.size());             // upsert, not a duplicate row
        assertEquals(AttendanceStatus.BUNK, all.get(0).getAttendanceStatus());
        assertEquals(END, all.get(0).getEndTime());
    }

    // Repo writes run async on AppDatabase.databaseWriteExecutor → poll for them.
    private Attendance await() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Attendance a = db.attendanceDao().getSpecificAttendance(subjectId, DATE, START);
            if (a != null) return a;
            Thread.sleep(10);
        }
        return null;
    }

    private void awaitStatus(int status) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Attendance a = db.attendanceDao().getSpecificAttendance(subjectId, DATE, START);
            if (a != null && a.getStatus() == status) return;
            Thread.sleep(10);
        }
    }
}
