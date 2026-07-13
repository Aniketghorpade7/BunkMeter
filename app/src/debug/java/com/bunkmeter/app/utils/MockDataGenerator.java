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

                // 2. CREATE CLASSROOMS
                Classroom r1 = new Classroom(); 
                r1.setName("Room 101"); 
                r1.setLatitude(16.7123); 
                r1.setLongitude(74.2145); 
                r1.setRadius(25f); 
                r1.setActive(true);

                db.classroomDao().insert(r1);

                List<Classroom> savedRooms = db.classroomDao().getActiveClassrooms();
                int idRoom101 = savedRooms.get(0).getClassroomId();

                // 3. CREATE SUBJECTS
                db.subjectDao().insertSubject(new Subject("Java Programming", "#FF5722"));
                db.subjectDao().insertSubject(new Subject("Operating Systems", "#4CAF50"));
                db.subjectDao().insertSubject(new Subject("Database Management", "#2196F3"));
                db.subjectDao().insertSubject(new Subject("Computer Networks", "#FFC107"));

                List<Subject> savedSubjects = db.subjectDao().getAllSubjects();
                int[] subjects = new int[4];
                for(int i = 0; i < 4; i++) {
                    subjects[i] = savedSubjects.get(i).getSubjectId();
                }

                // 4. CREATE TIMETABLE 
                List<Timetable> weeklySchedule = new ArrayList<>();
                for (int day = 0; day < 5; day++) { // Mon-Fri
                    Timetable t1 = new Timetable(subjects[0], day, 480, 540, idRoom101, "Lecture"); // 8:00
                    db.timetableDao().insertTimetable(t1); weeklySchedule.add(t1);
                    
                    Timetable t2 = new Timetable(subjects[1], day, 555, 615, idRoom101, "Lecture"); // 9:15
                    db.timetableDao().insertTimetable(t2); weeklySchedule.add(t2);

                    Timetable t3 = new Timetable(subjects[2], day, 630, 690, idRoom101, "Lecture"); // 10:30
                    db.timetableDao().insertTimetable(t3); weeklySchedule.add(t3);
                    
                    Timetable t4 = new Timetable(subjects[3], day, 720, 780, idRoom101, "Lecture"); // 12:00
                    db.timetableDao().insertTimetable(t4); weeklySchedule.add(t4);
                }

                // 5. CREATE 3 MONTHS EXACT REQUIREMENT ATTENDANCE 
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Calendar calendar = Calendar.getInstance();
                int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                int daysToStartOfWeek = (currentDayOfWeek == Calendar.SUNDAY) ? 6 : currentDayOfWeek - Calendar.MONDAY;
                calendar.add(Calendar.DAY_OF_YEAR, -daysToStartOfWeek); // Shift to this week's Monday
                calendar.add(Calendar.DAY_OF_YEAR, -13 * 7); // Go back ~3 months (13 weeks)
                
                Random random = new Random();

                for (int week = 0; week < 14; week++) { // Past 13 weeks + current week
                    // Map distinct statuses for each subject for this particular week
                    java.util.Map<Integer, List<Integer>> subjectWeeklyStatus = new java.util.HashMap<>();
                    for (int subId : subjects) {
                        int numPresent = 2 + random.nextInt(3); // 2, 3, or 4
                        int numAbsent = 1;
                        int numCancelled = 5 - numPresent - numAbsent; // remaining
                        
                        List<Integer> statuses = new ArrayList<>();
                        for (int k = 0; k < numPresent; k++) statuses.add(1); // Present = 1
                        for (int k = 0; k < numAbsent; k++) statuses.add(0); // Absent = 0
                        for (int k = 0; k < numCancelled; k++) statuses.add(2); // Cancelled = 2
                        java.util.Collections.shuffle(statuses, random);
                        subjectWeeklyStatus.put(subId, statuses);
                    }

                    // Iterate over 5 days (Mon-Fri)
                    for (int dayIndex = 0; dayIndex < 5; dayIndex++) {
                        // Avoid generating future days beyond today!
                        if (calendar.getTimeInMillis() > System.currentTimeMillis()) {
                            calendar.add(Calendar.DAY_OF_YEAR, 1);
                            continue; 
                        }
                        
                        String dateString = sdf.format(calendar.getTime());
                        for (Timetable scheduledClass : weeklySchedule) {
                            if (scheduledClass.getDayOfWeek() == dayIndex) {
                                Attendance a = new Attendance();
                                a.setSubjectId(scheduledClass.getSubjectId());
                                a.setClassroomId(scheduledClass.getClassroomId());
                                a.setDate(dateString);
                                a.setStartTime(scheduledClass.getStartTime());
                                a.setEndTime(scheduledClass.getEndTime());
                                
                                int status = subjectWeeklyStatus.get(scheduledClass.getSubjectId()).get(dayIndex);
                                a.setStatus(status);
                                
                                db.attendanceDao().insertAttendance(a);
                            }
                        }
                        calendar.add(Calendar.DAY_OF_YEAR, 1); // Move to next weekday
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, 2); // Skip Sat/Sun to Next Monday
                }

                // Notify UI of Success
                activity.runOnUiThread(() ->
                        Toast.makeText(context, "✅ App populated with 3 months of explicit test data!", Toast.LENGTH_LONG).show()
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