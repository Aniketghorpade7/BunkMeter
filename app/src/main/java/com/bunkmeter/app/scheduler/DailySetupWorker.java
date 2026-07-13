package com.bunkmeter.app.scheduler;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.utils.AttendanceLogic;
import com.bunkmeter.app.utils.DateUtils;

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

        int mappedDay = DateUtils.calendarToAppDay(currentDayOfWeek);

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

            // One deterministic session id per lecture, from the shared helper so
            // it matches exactly what GeofenceBroadcastReceiver/AttendanceActionReceiver
            // use to CANCEL these jobs. Used as the WorkManager tag SESSION_<id>.
            int sessionId = AttendanceLogic.sessionId(lecture.getSubjectId(), todayDate, lecture.getStartTime());
            String sessionTag = AttendanceLogic.sessionTag(lecture.getSubjectId(), todayDate, lecture.getStartTime());

            Data lectureData = new Data.Builder()
                    .putInt("subject_id", lecture.getSubjectId())
                    .putInt("start_time", lecture.getStartTime())
                    .putInt("end_time", lecture.getEndTime())
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
                        .addTag(sessionTag)
                        .build();
                workManager.enqueue(ongoingReq);
            }

            // A subject with no classroom can't be auto-tracked by geofencing,
            // so flag it and prompt the user to assign one. (Automatic attendance
            // itself is now handled entirely by the OS geofence registered in
            // GeofenceManager — no per-lecture GPS jobs are scheduled here.)
            if (lecture.getClassroomId() == null) {
                missingClassroom = true;
            }

            // Auto-bunk safety net: if the lecture is STILL "Pending" by the time it
            // ends, AutoBunkWorker records a BUNK so the stat isn't left dangling.
            // It shares the sessionTag, so it's cancelled the moment the student is
            // marked present (geofence) or taps a notification button.
            long autoBunkDelay = lecture.getEndTime() - currentMins;
            if (autoBunkDelay > 0) {
                OneTimeWorkRequest autoBunkReq = new OneTimeWorkRequest.Builder(AutoBunkWorker.class)
                        .setInitialDelay(autoBunkDelay, TimeUnit.MINUTES)
                        .setInputData(lectureData)
                        .addTag(NotificationScheduler.TAG_TODAYS_SCHEDULE)
                        .addTag(sessionTag)
                        .build();
                workManager.enqueue(autoBunkReq);
            }

            // --- Interactive Lecture-Start Prompt ---
            long interactivePromptDelay = lecture.getStartTime() - currentMins;

            if (interactivePromptDelay >= 0) {
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