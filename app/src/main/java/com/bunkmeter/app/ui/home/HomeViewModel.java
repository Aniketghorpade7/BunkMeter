package com.bunkmeter.app.ui.home;

import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.HomeLectureItem;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.AttendanceRepository;
import com.bunkmeter.app.utils.AttendanceLogic;
import com.bunkmeter.app.utils.DateUtils;

import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private final AttendanceRepository attendanceRepo;
    private final LiveData<List<HomeLectureItem>> todaysLectures;
    private final String todayDate;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        attendanceRepo = new AttendanceRepository(application);

        todayDate = DateUtils.todayDateString();

        // Day mapping now comes from the shared DateUtils, which correctly
        // includes Saturday (= 5). The old private copy here returned -1 for
        // Saturday, so Saturday lectures never showed on the Home screen even
        // though the background workers fired. (Bug #1 fixed.)
        int mappedDay = DateUtils.todayAppDay();

        AppDatabase db = AppDatabase.getInstance(application);

        // Room's reactive JOIN query — the UI auto-updates whenever any
        // Attendance, Subject, or Classroom row changes. No manual refresh needed.
        if (mappedDay != DateUtils.SUNDAY_NO_LECTURES) {
            todaysLectures = db.timetableDao().getTodaysLecturesLive(mappedDay, todayDate);
        } else {
            // Sunday — no lectures
            todaysLectures = new androidx.lifecycle.MutableLiveData<>(new java.util.ArrayList<>());
        }
    }

    public LiveData<List<HomeLectureItem>> getTodaysLectures() {
        return todaysLectures;
    }

    public String getTodayDateString() {
        return todayDate;
    }

    /**
     * Called from the UI when a Present/Bunk/Cancel button is tapped.
     * Room automatically emits a new LiveData value, updating the list.
     */
    public void markAttendance(int subjectId, int startTime, Integer classroomId, int status) {
        int roomId = (classroomId != null && classroomId > 0) ? classroomId : 0;
        attendanceRepo.updateAttendanceStatus(subjectId, todayDate, startTime, roomId, status);

        // Bug #3 fix: dismiss the ongoing "Are you in class?" notification that
        // LectureStartWorker/OngoingLectureWorker may have posted, so it doesn't
        // linger after the user already answered via the in-app buttons.
        cancelActiveLectureNotification(subjectId, startTime);

        // Bug #5 fix: only nudge to create a classroom once per day, mirroring the
        // debounce that DailySetupWorker uses — instead of re-posting on every tap.
        if (classroomId == null || classroomId == 0) {
            android.content.SharedPreferences prefs = getApplication()
                    .getSharedPreferences("bunkmeter_prefs", Context.MODE_PRIVATE);
            if (!todayDate.equals(prefs.getString("classroom_notif_date", ""))) {
                AttendanceNotificationHelper.triggerCreateClassroomNotification(getApplication());
                prefs.edit().putString("classroom_notif_date", todayDate).apply();
            }
        }
    }

    /** Cancels the active-lecture notification using the same deterministic ID it was posted with. */
    private void cancelActiveLectureNotification(int subjectId, int startTime) {
        int activeNotifId = AttendanceLogic.activeNotificationId(subjectId, todayDate, startTime);
        NotificationManager nm =
                (NotificationManager) getApplication().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(activeNotifId);
    }
}
