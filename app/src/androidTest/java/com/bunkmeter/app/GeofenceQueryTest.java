package com.bunkmeter.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Instrumented tests for the SQL that DRIVES geofencing auto-marking:
 * {@code TimetableDao.getActiveLecturesForClassroomSync(day, classroomId, nowMins)}.
 *
 * Runs on a device/emulator against a real (in-memory) SQLite database, so the
 * actual query and its 15-minute early-grace window are exercised end to end —
 * this is what decides whether a geofence ENTER event marks the student PRESENT.
 */
@RunWith(AndroidJUnit4.class)
public class GeofenceQueryTest {

    private static final int MON = 0;      // DateUtils app-day for Monday
    private static final int START = 570;  // 09:30
    private static final int END = 630;    // 10:30

    private AppDatabase db;
    private int subjectId;
    private int roomA;
    private int roomB;

    @Before
    public void setup() {
        Context ctx = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        subjectId = (int) db.subjectDao().insertSubject(new Subject("Math", "#FF0000"));

        Classroom a = new Classroom();
        a.setName("Room A");
        a.setRadius(100);
        Classroom b = new Classroom();
        b.setName("Room B");
        b.setRadius(100);
        db.classroomDao().insert(a);
        db.classroomDao().insert(b);
        List<Classroom> rooms = db.classroomDao().getActiveClassrooms();
        roomA = rooms.get(0).getClassroomId();
        roomB = rooms.get(1).getClassroomId();

        // Monday lecture 09:30–10:30 in Room A.
        db.timetableDao().insertTimetable(new Timetable(subjectId, MON, START, END, roomA, "Lecture"));
    }

    @After
    public void tearDown() {
        db.close();
    }

    private int hits(int day, int room, int now) {
        return db.timetableDao().getActiveLecturesForClassroomSync(day, room, now).size();
    }

    @Test
    public void marksWhenInsideWindow() {
        List<Timetable> found = db.timetableDao().getActiveLecturesForClassroomSync(MON, roomA, START);
        assertEquals(1, found.size());
        assertEquals(subjectId, found.get(0).getSubjectId());
    }

    @Test
    public void includes15MinEarlyGrace() {
        assertEquals(1, hits(MON, roomA, START - 15)); // exactly start-15 counts
    }

    @Test
    public void excludesMoreThan15MinEarly() {
        assertTrue(hits(MON, roomA, START - 16) == 0); // 16 min early → too early
    }

    @Test
    public void endTimeIsInclusive() {
        assertEquals(1, hits(MON, roomA, END));        // at end still counts
        assertEquals(0, hits(MON, roomA, END + 1));    // one minute after → gone
    }

    @Test
    public void excludesWrongRoom() {
        assertEquals(0, hits(MON, roomB, START));      // right time, wrong classroom
    }

    @Test
    public void excludesWrongDay() {
        assertEquals(0, hits(MON + 1, roomA, START));  // right room, wrong day
    }
}
