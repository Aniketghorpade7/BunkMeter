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

    // Synchronous query for the 7:00 AM DailySetupWorker
    @Query("SELECT * FROM Timetable WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    List<Timetable> getTimetableForDaySync(int dayOfWeek);

    // Reactive JOIN query for the Home Screen
    @Query("SELECT t.timetableId, t.subjectId, t.startTime, t.endTime, t.classroomId, " +
            "s.name AS subjectName, s.color AS subjectColor, c.name AS classroomName, " +
            "a.status AS attendanceStatus " +
            "FROM Timetable t " +
            "INNER JOIN Subject s ON t.subjectId = s.subjectId " +
            "LEFT JOIN Classroom c ON t.classroomId = c.classroomId " +
            "LEFT JOIN Attendance a ON t.subjectId = a.subjectId AND a.startTime = t.startTime AND a.date = :todayDate " +
            "WHERE t.dayOfWeek = :dayOfWeek " +
            "UNION " +
            "SELECT -1 AS timetableId, a.subjectId, a.startTime, a.endTime, a.classroomId, " +
            "s.name AS subjectName, s.color AS subjectColor, c.name AS classroomName, " +
            "CASE WHEN a.status = -3 THEN null ELSE a.status END AS attendanceStatus " +
            "FROM Attendance a " +
            "INNER JOIN Subject s ON a.subjectId = s.subjectId " +
            "LEFT JOIN Classroom c ON a.classroomId = c.classroomId " +
            "LEFT JOIN Timetable t2 ON a.subjectId = t2.subjectId AND a.startTime = t2.startTime AND t2.dayOfWeek = :dayOfWeek " +
            "WHERE a.date = :todayDate AND t2.timetableId IS NULL " +
            "ORDER BY 3 ASC")
    androidx.lifecycle.LiveData<java.util.List<com.bunkmeter.app.model.HomeLectureItem>> getTodaysLecturesLive(int dayOfWeek, String todayDate);

    // Synchronous JOIN query for DailySetupWorker including pending Extra lectures
    @Query("SELECT timetableId, subjectId, dayOfWeek, startTime, endTime, classroomId, type FROM Timetable WHERE dayOfWeek = :dayOfWeek " +
           "UNION " +
           "SELECT -1 AS timetableId, a.subjectId, -1 AS dayOfWeek, a.startTime, a.endTime, a.classroomId, 'Extra' AS type " +
           "FROM Attendance a " +
           "LEFT JOIN Timetable t2 ON a.subjectId = t2.subjectId AND a.startTime = t2.startTime AND t2.dayOfWeek = :dayOfWeek " +
           "WHERE a.date = :todayDate AND t2.timetableId IS NULL " +
           "ORDER BY 4 ASC")
    List<Timetable> getTimetableAndExtraForDaySync(int dayOfWeek, String todayDate);

    // Lookup classroom for attendance insertion
    @Query("SELECT * FROM Timetable WHERE subjectId = :subjectId AND dayOfWeek = :dayOfWeek AND startTime = :startTime LIMIT 1")
    com.bunkmeter.app.model.Timetable getTimetableForSubjectAndTimeSync(int subjectId, int dayOfWeek, int startTime);
}