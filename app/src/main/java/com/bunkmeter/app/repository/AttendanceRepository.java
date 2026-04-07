package com.bunkmeter.app.repository;

import android.app.Application;

import com.bunkmeter.app.database.AttendanceDao;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;

import java.util.List;

public class AttendanceRepository {

    private final AttendanceDao attendanceDao;

    public AttendanceRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        attendanceDao = db.attendanceDao();
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
}