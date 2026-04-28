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

    public void updateAttendanceStatus(int subjectId, String date, int startTime,
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
                newAttendance.setStatus(status);
                newAttendance.setLocationVerified(false);

                // If classroomId is not provided, try to find it from Timetable
                if (classroomId <= 0) {
                    try {
                        java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                        int dayOfWeekInput = localDate.getDayOfWeek().getValue(); // 1=Mon ... 7=Sun

                        // Map to our 0=Mon convention
                        int mappedDay = -1;
                        if (dayOfWeekInput == 1) mappedDay = 0; // MONDAY
                        else if (dayOfWeekInput == 2) mappedDay = 1; // TUESDAY
                        else if (dayOfWeekInput == 3) mappedDay = 2; // WEDNESDAY
                        else if (dayOfWeekInput == 4) mappedDay = 3; // THURSDAY
                        else if (dayOfWeekInput == 5) mappedDay = 4; // FRIDAY

                        if (mappedDay != -1) {
                            com.bunkmeter.app.model.Timetable t =
                                    timetableDao.getTimetableForSubjectAndTimeSync(subjectId, mappedDay, startTime);
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

                attendanceDao.insertAttendance(newAttendance);
            }
        });
    }

    public void updateAttendanceStatus(int subjectId, String date, int startTime,
                                       int classroomId, AttendanceStatus status) {
        updateAttendanceStatus(subjectId, date, startTime, classroomId, status.value);
    }

    public Attendance getSpecificAttendanceSync(int subjectId, String date, int startTime) {
        return attendanceDao.getSpecificAttendance(subjectId, date, startTime);
    }

    public LiveData<List<Attendance>> getLiveAttendanceBySubject(int subjectId) {
        return attendanceDao.getLiveAttendanceBySubject(subjectId);
    }
}