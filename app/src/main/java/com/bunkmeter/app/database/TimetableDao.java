package com.bunkmeter.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.bunkmeter.app.model.Timetable;

import java.util.List;

@Dao
public interface TimetableDao
{
    @Query("SELECT * FROM timetable")
    List<Timetable> getAll();

    @Insert
    long insertTimetable(Timetable timetable);

    @Update
    void updateTimetable(Timetable timetable);

    @Delete
    void deleteTimetable(Timetable timetable);

    // Get full timetable sorted
    @Query("SELECT * FROM Timetable ORDER BY dayOfWeek, startTime")
    List<Timetable> getFullTimetable();

    // Get timetable for a specific day
    @Query("SELECT * FROM Timetable WHERE dayOfWeek = :day ORDER BY startTime ASC")
    List<Timetable> getTimetableForDay(int day);

    // Conflict detection query (CORE LOGIC)
    @Query("SELECT * FROM Timetable WHERE dayOfWeek = :day " +
            "AND (:startTime < endTime AND :endTime > startTime)")
    List<Timetable> getConflictingSlots(int day, int startTime, int endTime);

    // Delete conflicts (for replace feature)
    @Query("DELETE FROM Timetable WHERE dayOfWeek = :day " +
            "AND (:startTime < endTime AND :endTime > startTime)")
    void deleteConflicts(int day, int startTime, int endTime);
}