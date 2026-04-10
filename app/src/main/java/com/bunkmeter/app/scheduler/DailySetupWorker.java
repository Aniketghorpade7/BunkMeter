package com.bunkmeter.app.scheduler;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DailySetupWorker extends Worker {

    public DailySetupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Calendar calendar = Calendar.getInstance();
        int todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2...
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<Timetable> todaysLectures = db.timetableDao().getTimetableForDaySync(todayDayOfWeek);

        WorkManager workManager = WorkManager.getInstance(getApplicationContext());

        // 1. GREETING NOTIFICATION LOGIC
        long greetingDelayMinutes;
        if (todaysLectures.isEmpty()) {
            // No lectures? Schedule greeting for 8:45 AM (105 mins from 7:00 AM)
            greetingDelayMinutes = 105;
        } else {
            // First lecture minus 30 mins
            int firstLectureStartMinutes = todaysLectures.get(0).getStartTime();
            int currentMinutesFromMidnight = 7 * 60; // 7:00 AM
            greetingDelayMinutes = (firstLectureStartMinutes - 30) - currentMinutesFromMidnight;
            if (greetingDelayMinutes < 0) greetingDelayMinutes = 0;
        }

        Data greetingData = new Data.Builder().putInt("lecture_count", todaysLectures.size()).build();
        OneTimeWorkRequest greetingRequest = new OneTimeWorkRequest.Builder(GreetingWorker.class)
                .setInitialDelay(greetingDelayMinutes, TimeUnit.MINUTES)
                .setInputData(greetingData)
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();
        workManager.enqueue(greetingRequest);

        // 2. LECTURE-SPECIFIC NOTIFICATIONS + LOCATION CHECKS
        int currentMins = 7 * 60; // Workers are enqueued at 7:00 AM
        boolean missingClassroom = false; // tracks if any lecture has no classroom assigned

        for (Timetable lecture : todaysLectures) {
            long preLectureDelay  = (lecture.getStartTime() - 10) - currentMins;
            long ongoingLectureDelay = (lecture.getStartTime() + 30) - currentMins;

            Data lectureData = new Data.Builder()
                    .putInt("subject_id", lecture.getSubjectId())
                    .putInt("start_time", lecture.getStartTime())
                    .putString("date", todayDate)
                    .build();

            // 10 Mins Before — heads-up notification
            if (preLectureDelay > 0) {
                OneTimeWorkRequest preReq = new OneTimeWorkRequest.Builder(PreLectureWorker.class)
                        .setInitialDelay(preLectureDelay, TimeUnit.MINUTES)
                        .setInputData(lectureData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .build();
                workManager.enqueue(preReq);
            }

            // 30 Mins After — checks if attendance was already marked, or starts foreground prompt
            if (ongoingLectureDelay > 0) {
                OneTimeWorkRequest ongoingReq = new OneTimeWorkRequest.Builder(OngoingLectureWorker.class)
                        .setInitialDelay(ongoingLectureDelay, TimeUnit.MINUTES)
                        .setInputData(lectureData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .build();
                workManager.enqueue(ongoingReq);
            }

            // 3. LOCATION-BASED AUTO ATTENDANCE
            // Only schedule if this lecture has a classroom with GPS coordinates set
            Integer classroomId = lecture.getClassroomId();
            if (classroomId != null) {
                Classroom classroom = db.classroomDao().getClassroomById(classroomId);
                if (classroom != null) {
                    // Delay from now (7 AM) until the lecture starts
                    long lectureStartDelay = lecture.getStartTime() - currentMins;
                    if (lectureStartDelay > 0) {
                        // Use timetableId as a stable lectureId so TempReadingStorage keys are unique
                        long lectureId = lecture.getTimetableId();
                        AttendanceScheduler.scheduleAttendanceCheck(
                                getApplicationContext(),
                                lectureId,
                                lecture.getSubjectId(),
                                todayDate,
                                lecture.getStartTime(),
                                classroom.getLatitude(),
                                classroom.getLongitude(),
                                classroom.getRadius()
                        );
                    }
                }
            } else {
                // Track that at least one lecture has no classroom assigned
                missingClassroom = true;
            }
        }

        // Fire the create-classroom notification ONCE per day if any lecture had no classroom
        if (missingClassroom) {
            android.content.SharedPreferences prefs = getApplicationContext()
                    .getSharedPreferences("bunkmeter_prefs", android.content.Context.MODE_PRIVATE);
            String lastNotifiedDay = prefs.getString("classroom_notif_date", "");
            if (!todayDate.equals(lastNotifiedDay)) {
                AttendanceNotificationHelper.triggerCreateClassroomNotification(getApplicationContext());
                prefs.edit().putString("classroom_notif_date", todayDate).apply();
            }
        }

        return Result.success();
    }
}