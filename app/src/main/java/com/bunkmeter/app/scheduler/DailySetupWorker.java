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
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int currentMins = (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE);

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());

        int mappedDay = getMappedDay(currentDayOfWeek);

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        WorkManager workManager = WorkManager.getInstance(getApplicationContext());

        if (mappedDay == -1) {
            scheduleGreeting(workManager, 0, currentMins, 0);
            return Result.success();
        }

        List<Timetable> todaysLectures = db.timetableDao().getTimetableAndExtraForDaySync(mappedDay, todayDate);

        if (todaysLectures.isEmpty()) {
            scheduleGreeting(workManager, 0, currentMins, 0);
        } else {
            scheduleGreeting(workManager, todaysLectures.size(), currentMins, todaysLectures.get(0).getStartTime());
        }

        boolean missingClassroom = false;

        for (Timetable lecture : todaysLectures) {
            long preLectureDelay  = (lecture.getStartTime() - 10) - currentMins;
            long ongoingLectureDelay = (lecture.getStartTime() + 30) - currentMins;

            Data lectureData = new Data.Builder()
                    .putInt("subject_id", lecture.getSubjectId())
                    .putInt("start_time", lecture.getStartTime())
                    .putString("date", todayDate)
                    .build();

            if (preLectureDelay > 0) {
                OneTimeWorkRequest preReq = new OneTimeWorkRequest.Builder(PreLectureWorker.class)
                        .setInitialDelay(preLectureDelay, TimeUnit.MINUTES)
                        .setInputData(lectureData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .build();
                workManager.enqueue(preReq);
            }

            if (ongoingLectureDelay > 0) {
                OneTimeWorkRequest ongoingReq = new OneTimeWorkRequest.Builder(OngoingLectureWorker.class)
                        .setInitialDelay(ongoingLectureDelay, TimeUnit.MINUTES)
                        .setInputData(lectureData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .build();
                workManager.enqueue(ongoingReq);
            }

            Integer classroomId = lecture.getClassroomId();
            if (classroomId != null) {
                Classroom classroom = db.classroomDao().getClassroomById(classroomId);
                if (classroom != null) {
                    long autoStartDelay = lecture.getStartTime() - currentMins; // Renamed to avoid conflict
                    if (autoStartDelay > 0) {
                        long lectureId = lecture.getTimetableId();
                        if (lectureId == -1) {
                            lectureId = -((long)lecture.getSubjectId() * 10000L + lecture.getStartTime());
                        }
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
                missingClassroom = true;
            }

            // --- Interactive Lecture-Start Prompt ---
            long interactivePromptDelay = lecture.getStartTime() - currentMins; // Renamed to avoid conflict

            if (interactivePromptDelay >= 0) {
                int sessionId = Math.abs(java.util.Objects.hash(lecture.getSubjectId(), todayDate, lecture.getStartTime()));
                if (sessionId == 0) sessionId = 1;

                Data startData = new Data.Builder()
                        .putInt("subject_id", lecture.getSubjectId())
                        .putInt("start_time", lecture.getStartTime())
                        .putInt("end_time", lecture.getEndTime())
                        .putString("date", todayDate)
                        .putInt("session_id", sessionId)
                        .build();

                OneTimeWorkRequest startReq = new OneTimeWorkRequest.Builder(LectureStartWorker.class)
                        .setInitialDelay(interactivePromptDelay, TimeUnit.MINUTES)
                        .setInputData(startData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .build();
                workManager.enqueue(startReq);
            }
        }

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

    private int getMappedDay(int currentDayOfWeek) {
        if (currentDayOfWeek == Calendar.MONDAY)    return 0;
        if (currentDayOfWeek == Calendar.TUESDAY)   return 1;
        if (currentDayOfWeek == Calendar.WEDNESDAY) return 2;
        if (currentDayOfWeek == Calendar.THURSDAY)  return 3;
        if (currentDayOfWeek == Calendar.FRIDAY)    return 4;
        if (currentDayOfWeek == Calendar.SATURDAY)  return 5;
        return -1;
    }

    private void scheduleGreeting(WorkManager workManager, int lectureCount, int currentMins, int firstLectureStartMins) {
        if (currentMins >= 12 * 60) return;

        long greetingDelayMinutes;

        if (lectureCount == 0) {
            greetingDelayMinutes = 525 - currentMins;
        } else {
            greetingDelayMinutes = (firstLectureStartMins - 30) - currentMins;
        }

        if (greetingDelayMinutes < 0) {
            greetingDelayMinutes = 0;
        }

        Data greetingData = new Data.Builder().putInt("lecture_count", lectureCount).build();
        OneTimeWorkRequest greetingRequest = new OneTimeWorkRequest.Builder(GreetingWorker.class)
                .setInitialDelay(greetingDelayMinutes, TimeUnit.MINUTES)
                .setInputData(greetingData)
                .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                .build();

        workManager.enqueue(greetingRequest);
    }
}