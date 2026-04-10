package com.bunkmeter.app.ui.home;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.HomeLectureItem;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.AttendanceRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeViewModel extends AndroidViewModel {

    private final AttendanceRepository attendanceRepo;
    private final LiveData<List<HomeLectureItem>> todaysLectures;
    private final String todayDate;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        attendanceRepo = new AttendanceRepository(application);

        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Calendar calendar = Calendar.getInstance();
        int mappedDay = getMappedDay(calendar.get(Calendar.DAY_OF_WEEK));

        AppDatabase db = AppDatabase.getInstance(application);

        // Use Room's reactive JOIN query — the UI will auto-update whenever
        // any Attendance, Subject, or Classroom row changes. No manual refresh needed.
        if (mappedDay != -1) {
            todaysLectures = db.timetableDao().getTodaysLecturesLive(mappedDay, todayDate);
        } else {
            // Weekend — no lectures
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
     * Called from UI when a button is clicked.
     * Room will automatically emit a new LiveData value, updating the UI list.
     * If the subject has no classroom assigned, we also fire a notification prompting
     * the user to set one up (needed for auto location-based tracking to work).
     */
    public void markAttendance(int subjectId, int startTime, Integer classroomId, int status) {
        int roomId = (classroomId != null && classroomId > 0) ? classroomId : 0;
        attendanceRepo.updateAttendanceStatus(subjectId, todayDate, startTime, roomId, status);

        // Notify the user to create a classroom if this subject has none assigned
        if (classroomId == null || classroomId == 0) {
            AttendanceNotificationHelper.triggerCreateClassroomNotification(getApplication());
        }
        // No need to call loadMergedData() — the LiveData observer fires automatically
    }

    /**
     * Maps Java Calendar day constants to the app's 0=Mon…4=Fri convention used in Timetable.
     */
    private int getMappedDay(int currentDayOfWeek) {
        if (currentDayOfWeek == Calendar.MONDAY)    return 0;
        if (currentDayOfWeek == Calendar.TUESDAY)   return 1;
        if (currentDayOfWeek == Calendar.WEDNESDAY) return 2;
        if (currentDayOfWeek == Calendar.THURSDAY)  return 3;
        if (currentDayOfWeek == Calendar.FRIDAY)    return 4;
        return -1; // Saturday / Sunday
    }
}