package com.bunkmeter.app.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.bunkmeter.app.model.Attendance;

import java.util.List;

@Dao
public interface AttendanceDao
{
    @Insert
    long insertAttendance(Attendance attendance);

    // Get attendance for a subject
    @Query("SELECT * FROM Attendance WHERE subjectId = :subjectId")
    List<Attendance> getAttendanceBySubject(int subjectId);

    // Count total lectures
    @Query("SELECT COUNT(*) FROM Attendance WHERE subjectId = :subjectId")
    int getTotalClasses(int subjectId);

    // Count present lectures
    @Query("SELECT COUNT(*) FROM Attendance WHERE subjectId = :subjectId AND status = 1")
    int getPresentClasses(int subjectId);

    // Count absent lectures
    @Query("SELECT COUNT(*) FROM Attendance WHERE subjectId = :subjectId AND status = 0")
    int getAbsentClasses(int subjectId);

    // For Excel imports all the attandance
    @Query("SELECT * FROM Attendance")
    List<Attendance> getAllAttendanceSync();

    // Updates an existing record if the user changes their mind
    @Update
    void updateAttendance(Attendance attendance);

    // Checks if attendance was already marked for this specific class today
    @Query("SELECT * FROM Attendance WHERE subjectId = :subjectId AND date = :date AND startTime = :startTime LIMIT 1")
    Attendance getSpecificAttendance(int subjectId, String date, int startTime);

    @Query("SELECT * FROM Attendance WHERE date = :date")
    List<Attendance> getAttendanceForDate(String date);

    // Reactive query for the Home Screen to observe live changes (MVVM)
    @Query("SELECT * FROM Attendance WHERE date = :date")
    LiveData<List<Attendance>> getLiveAttendanceForDate(String date);

    // Update status directly from Notification actions
    @Query("UPDATE Attendance SET status = :status WHERE subjectId = :subjectId AND date = :date AND startTime = :startTime")
    void updateAttendanceStatus(int subjectId, String date, int startTime, int status);

    // Reactive query for the Subject Screen
    @Query("SELECT * FROM Attendance WHERE subjectId = :subjectId ORDER BY date DESC, startTime DESC")
    androidx.lifecycle.LiveData<java.util.List<Attendance>> getLiveAttendanceBySubject(int subjectId);
}