package com.bunkmeter.app.database;

import androidx.room.Dao;
import androidx.room.Query;

@Dao
public interface ResetDao {

    // The order is important to respect foreign key constraints (if you have them)
    // Child tables are cleared before parent tables.

    @Query("DELETE FROM Attendance")
    void deleteAttendance();

    @Query("DELETE FROM Timetable")
    void deleteTimetable();

    @Query("DELETE FROM Subject")
    void deleteSubjects();

    @Query("DELETE FROM Classroom")
    void deleteClassrooms();
}