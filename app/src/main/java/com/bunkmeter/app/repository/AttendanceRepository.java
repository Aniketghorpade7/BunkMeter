package com.bunkmeter.app.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.bunkmeter.app.database.AttendanceDao;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import java.util.List;

public class AttendanceRepository {

    private final AttendanceDao attendanceDao;
    private final com.bunkmeter.app.database.TimetableDao timetableDao;

    public AttendanceRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        attendanceDao = db.attendanceDao();
        timetableDao = db.timetableDao();
    }

    public void insert(Attendance attendance) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                attendanceDao.insertAttendance(attendance));
    }

    public List<Attendance> getBySubject(int subjectId) {
        return attendanceDao.getAttendanceBySubject(subjectId);
    }

    public int getTotal(int subjectId) {
        return attendanceDao.getTotalClasses(subjectId);
    }

    public int getPresent(int subjectId) {
        return attendanceDao.getPresentClasses(subjectId);
    }

    public int getAbsent(int subjectId) {
        return attendanceDao.getAbsentClasses(subjectId);
    }

    // Used by UI (HomeViewModel)
    public LiveData<List<Attendance>> getLiveAttendanceForDate(String date) {
        return attendanceDao.getLiveAttendanceForDate(date);
    }

    // Used by BroadcastReceiver and HomeFragment to mark Attendance
    public void updateAttendanceStatus(int subjectId, String date, int startTime, int classroomId, int status) {
        // Use your existing databaseWriteExecutor!
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Attendance existing = attendanceDao.getSpecificAttendance(subjectId, date, startTime);
            if (existing != null) {
                attendanceDao.updateAttendanceStatus(subjectId, date, startTime, status);
            } else {
                Attendance newAttendance = new Attendance();
                newAttendance.setSubjectId(subjectId);
                newAttendance.setDate(date);
                newAttendance.setStartTime(startTime);
                newAttendance.setStatus(status);
                newAttendance.setLocationVerified(false);
                
                // If classroomId is not provided, try to find it from Timetable
                if (classroomId <= 0) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                        java.util.Date d = sdf.parse(date);
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTime(d);
                        int dayOfWeekInput = cal.get(java.util.Calendar.DAY_OF_WEEK);
                        
                        // Map to our 0=Mon convention
                        int mappedDay = -1;
                        if (dayOfWeekInput == java.util.Calendar.MONDAY) mappedDay = 0;
                        else if (dayOfWeekInput == java.util.Calendar.TUESDAY) mappedDay = 1;
                        else if (dayOfWeekInput == java.util.Calendar.WEDNESDAY) mappedDay = 2;
                        else if (dayOfWeekInput == java.util.Calendar.THURSDAY) mappedDay = 3;
                        else if (dayOfWeekInput == java.util.Calendar.FRIDAY) mappedDay = 4;

                        if (mappedDay != -1) {
                            com.bunkmeter.app.model.Timetable t = timetableDao.getTimetableForSubjectAndTimeSync(subjectId, mappedDay, startTime);
                            if (t != null && t.getClassroomId() != null) {
                                newAttendance.setClassroomId(t.getClassroomId());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    newAttendance.setClassroomId(classroomId);
                }

                // If still no valid classroomId (e.g. extra lecture), this might still fail if schema requires it.
                // However, most entries should have it. 
                // We assume there's at least one default classroom if needed, or we just try to insert.
                attendanceDao.insertAttendance(newAttendance);
            }
        });
    }

    public Attendance getSpecificAttendanceSync(int subjectId, String date, int startTime) {
        return attendanceDao.getSpecificAttendance(subjectId, date, startTime);
    }

    public LiveData<List<Attendance>> getLiveAttendanceBySubject(int subjectId) {
        return attendanceDao.getLiveAttendanceBySubject(subjectId);
    }
}