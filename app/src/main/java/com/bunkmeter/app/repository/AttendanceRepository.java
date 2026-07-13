package com.bunkmeter.app.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.bunkmeter.app.database.AttendanceDao;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.AttendanceStatus;
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

    public LiveData<List<Attendance>> getLiveAttendanceForDate(String date) {
        return attendanceDao.getLiveAttendanceForDate(date);
    }

    /**
     * Legacy entry point (no endTime supplied). Kept so existing callers compile.
     * Passing endTime = 0 tells the canonical method to look the real endTime up
     * from the Timetable, which fixes the old bug where new rows stored endTime = 0.
     */
    public void updateAttendanceStatus(int subjectId, String date, int startTime,
                                       int classroomId, int status) {
        updateAttendanceStatus(subjectId, date, startTime, 0, classroomId, status);
    }

    /**
     * Canonical upsert. If a record for (subject, date, startTime) exists we just
     * update its status; otherwise we insert a complete new row.
     *
     * <p><b>endTime handling (bug fix):</b> the previous version never set endTime
     * on inserts, so every auto-created row stored endTime = 0 and corrupted any
     * duration-based stat. Now: if a real endTime is passed we use it; if 0 is
     * passed we look it up from the Timetable alongside the classroom.</p>
     */
    public void updateAttendanceStatus(int subjectId, String date, int startTime, int endTime,
                                       int classroomId, int status) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Attendance existing = attendanceDao.getSpecificAttendance(subjectId, date, startTime);
            if (existing != null) {
                attendanceDao.updateAttendanceStatus(subjectId, date, startTime, status);
            } else {
                Attendance newAttendance = new Attendance();
                newAttendance.setSubjectId(subjectId);
                newAttendance.setDate(date);
                newAttendance.setStartTime(startTime);
                newAttendance.setEndTime(endTime); // may be overwritten by the lookup below
                newAttendance.setStatus(status);
                newAttendance.setLocationVerified(false);

                // If classroom OR endTime is missing, fill them from the Timetable.
                if (classroomId <= 0 || endTime <= 0) {
                    try {
                        // Day-of-week mapping now lives in one place (fixes the old
                        // Saturday gap and the duplicated mapping logic).
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        cal.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .parse(date));
                        int mappedDay = com.bunkmeter.app.utils.DateUtils.calendarToAppDay(
                                cal.get(java.util.Calendar.DAY_OF_WEEK));

                        if (mappedDay != com.bunkmeter.app.utils.DateUtils.SUNDAY_NO_LECTURES) {
                            com.bunkmeter.app.model.Timetable t =
                                    timetableDao.getTimetableForSubjectAndTimeSync(subjectId, mappedDay, startTime);
                            if (t != null) {
                                if (classroomId <= 0 && t.getClassroomId() != null) {
                                    newAttendance.setClassroomId(t.getClassroomId());
                                }
                                if (endTime <= 0) {
                                    newAttendance.setEndTime(t.getEndTime());
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (classroomId > 0) {
                    newAttendance.setClassroomId(classroomId);
                }

                attendanceDao.insertAttendance(newAttendance);
            }
        });
    }

    public void updateAttendanceStatus(int subjectId, String date, int startTime,
                                       int classroomId, AttendanceStatus status) {
        updateAttendanceStatus(subjectId, date, startTime, 0, classroomId, status.value);
    }

    /** Endtime-aware enum overload used by geofencing and the auto-bunk worker. */
    public void updateAttendanceStatus(int subjectId, String date, int startTime, int endTime,
                                       int classroomId, AttendanceStatus status) {
        updateAttendanceStatus(subjectId, date, startTime, endTime, classroomId, status.value);
    }

    public Attendance getSpecificAttendanceSync(int subjectId, String date, int startTime) {
        return attendanceDao.getSpecificAttendance(subjectId, date, startTime);
    }

    public LiveData<List<Attendance>> getLiveAttendanceBySubject(int subjectId) {
        return attendanceDao.getLiveAttendanceBySubject(subjectId);
    }
}