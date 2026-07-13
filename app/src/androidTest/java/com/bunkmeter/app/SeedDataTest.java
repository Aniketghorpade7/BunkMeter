package com.bunkmeter.app;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.utils.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * NOT a behavioural test — a one-shot DB seeder that populates the emulator with
 * demo data for manual screenshots (Home statuses, Subject heatmap/pie, Timetable
 * grid). It writes to the REAL bunkmeter_db on purpose. Run it in isolation:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.bunkmeter.app.SeedDataTest
 */
@RunWith(AndroidJUnit4.class)
public class SeedDataTest {

    // Four daily slots (minutes from midnight): 08:00, 09:15, 10:30, 12:00
    private static final int[][] SLOTS = {{480, 540}, {555, 615}, {630, 690}, {720, 780}};

    @Test
    public void seed() {
        Context ctx = ApplicationProvider.getApplicationContext();
        AppDatabase db = AppDatabase.getInstance(ctx);
        db.clearAllTables();

        Classroom room = new Classroom();
        room.setName("Room 101");
        room.setLatitude(16.7123);
        room.setLongitude(74.2145);
        room.setRadius(25f);
        room.setActive(true);
        db.classroomDao().insert(room);
        int roomId = db.classroomDao().getActiveClassrooms().get(0).getClassroomId();

        db.subjectDao().insertSubject(new Subject("Java Programming", "#FF5722"));
        db.subjectDao().insertSubject(new Subject("Operating Systems", "#4CAF50"));
        db.subjectDao().insertSubject(new Subject("Database Management", "#2196F3"));
        db.subjectDao().insertSubject(new Subject("Computer Networks", "#FFC107"));
        List<Subject> subs = db.subjectDao().getAllSubjects();
        int[] sid = new int[subs.size()];
        for (int i = 0; i < subs.size(); i++) sid[i] = subs.get(i).getSubjectId();

        // Weekly timetable, Mon–Fri (app-days 0..4), 4 lectures/day.
        for (int day = 0; day < 5; day++) {
            for (int s = 0; s < 4; s++) {
                db.timetableDao().insertTimetable(
                        new Timetable(sid[s], day, SLOTS[s][0], SLOTS[s][1], roomId, "Lecture"));
            }
        }

        String today = DateUtils.todayDateString();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int toMonday = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
        cal.add(Calendar.DAY_OF_YEAR, -toMonday);   // this week's Monday
        cal.add(Calendar.DAY_OF_YEAR, -13 * 7);     // back ~13 weeks
        Random rnd = new Random(7);                 // fixed seed → reproducible screenshots

        for (int week = 0; week < 14; week++) {
            for (int day = 0; day < 5; day++) {
                if (cal.getTimeInMillis() > System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    continue; // never seed the future
                }
                String date = sdf.format(cal.getTime());

                if (date.equals(today)) {
                    // Curate TODAY so Home shows all four states at once:
                    // PRESENT, BUNKED, CANCELLED — and the 4th lecture left PENDING.
                    int[] todayStatus = {1, 0, 2}; // present, bunk, cancelled
                    for (int s = 0; s < 3; s++) {
                        db.attendanceDao().insertAttendance(
                                row(sid[s], roomId, today, SLOTS[s], todayStatus[s]));
                    }
                    // sid[3] intentionally has NO row today → shows as PENDING.
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    continue;
                }

                for (int s = 0; s < 4; s++) {
                    int r = rnd.nextInt(10);
                    int status = (r < 7) ? 1 : (r < 9 ? 0 : 2); // ~70% present, 20% bunk, 10% cancel
                    db.attendanceDao().insertAttendance(row(sid[s], roomId, date, SLOTS[s], status));
                }
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
            cal.add(Calendar.DAY_OF_YEAR, 2); // skip Sat/Sun
        }
    }

    private static Attendance row(int subjectId, int roomId, String date, int[] slot, int status) {
        Attendance a = new Attendance();
        a.setSubjectId(subjectId);
        a.setClassroomId(roomId);
        a.setDate(date);
        a.setStartTime(slot[0]);
        a.setEndTime(slot[1]);
        a.setStatus(status);
        return a;
    }
}
