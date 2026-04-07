package com.bunkmeter.app.utils;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

public class MockDataGenerator {

    public static void injectDummyData(Context context, Activity activity) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);

                activity.runOnUiThread(() -> Toast.makeText(context, "Wiping database and generating fresh data...", Toast.LENGTH_SHORT).show());

                // 1. WIPE EVERYTHING FOR A COMPLETELY CLEAN SLATE
                db.clearAllTables();

                // 2. CREATE CLASSROOMS & FETCH REAL IDs
                Classroom r1 = new Classroom(); r1.setName("Room 101"); r1.setLatitude(16.7); r1.setLongitude(74.2); r1.setRadius(20f); r1.setActive(true);
                Classroom r2 = new Classroom(); r2.setName("Computer Lab"); r2.setLatitude(16.71); r2.setLongitude(74.21); r2.setRadius(30f); r2.setActive(true);

                db.classroomDao().insert(r1);
                db.classroomDao().insert(r2);

                // Fetch them back since the insert method returns void
                List<Classroom> savedRooms = db.classroomDao().getActiveClassrooms();
                int idRoom101 = savedRooms.get(0).getClassroomId();
                int idLab = savedRooms.get(1).getClassroomId();

                // 3. CREATE SUBJECTS & FETCH REAL IDs
                db.subjectDao().insertSubject(new Subject("Java Programming", "#FF5722"));
                db.subjectDao().insertSubject(new Subject("Operating Systems", "#4CAF50"));
                db.subjectDao().insertSubject(new Subject("Database Management", "#2196F3"));
                db.subjectDao().insertSubject(new Subject("Computer Networks", "#FFC107"));

                List<Subject> savedSubjects = db.subjectDao().getAllSubjects();
                int[] subjects = new int[4];
                for(int i = 0; i < 4; i++) {
                    subjects[i] = savedSubjects.get(i).getSubjectId();
                }

                // 4. CREATE TIMETABLE (Using your insertTimetable method!)
                List<Timetable> weeklySchedule = new ArrayList<>();
                for (int day = 0; day < 5; day++) { // Mon-Fri
                    // Morning Lecture
                    Timetable t1 = new Timetable(subjects[day % 4], day, 540, 600, idRoom101, "Lecture");
                    db.timetableDao().insertTimetable(t1); // Updated method name!
                    weeklySchedule.add(t1);

                    // Afternoon Lab
                    Timetable t2 = new Timetable(subjects[(day + 1) % 4], day, 780, 900, idLab, "Lab");
                    db.timetableDao().insertTimetable(t2); // Updated method name!
                    weeklySchedule.add(t2);
                }

                // 5. CREATE ATTENDANCE WITH PERFECT FOREIGN KEYS
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, -30); // Go back 30 days
                Random random = new Random();

                for (int i = 0; i < 30; i++) {
                    int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

                    int mappedDay = -1;
                    if (currentDayOfWeek == Calendar.MONDAY) mappedDay = 0;
                    else if (currentDayOfWeek == Calendar.TUESDAY) mappedDay = 1;
                    else if (currentDayOfWeek == Calendar.WEDNESDAY) mappedDay = 2;
                    else if (currentDayOfWeek == Calendar.THURSDAY) mappedDay = 3;
                    else if (currentDayOfWeek == Calendar.FRIDAY) mappedDay = 4;

                    if (mappedDay != -1) { // If it is a weekday
                        String dateString = sdf.format(calendar.getTime());

                        for (Timetable scheduledClass : weeklySchedule) {
                            if (scheduledClass.getDayOfWeek() == mappedDay) {

                                Attendance a = new Attendance();
                                a.setSubjectId(scheduledClass.getSubjectId());
                                a.setClassroomId(scheduledClass.getClassroomId());
                                a.setDate(dateString);
                                a.setStartTime(scheduledClass.getStartTime());
                                a.setEndTime(scheduledClass.getEndTime());

                                // Randomize status (75% present, 25% bunked)
                                a.setStatus(random.nextInt(100) < 75 ? 1 : 0);

                                db.attendanceDao().insertAttendance(a);
                            }
                        }
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 1); // Move to next day
                }

                // Notify UI of Success
                activity.runOnUiThread(() ->
                        Toast.makeText(context, "✅ App fully populated from scratch!", Toast.LENGTH_LONG).show()
                );

            } catch (Throwable t) {
                t.printStackTrace();
                activity.runOnUiThread(() ->
                        Toast.makeText(context, "❌ Error: " + t.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        });
    }
}