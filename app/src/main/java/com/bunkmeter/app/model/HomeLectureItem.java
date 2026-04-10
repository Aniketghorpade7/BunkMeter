package com.bunkmeter.app.model;

import androidx.room.ColumnInfo;
import androidx.room.Ignore;

public class HomeLectureItem {
    public int timetableId;     // -1 if it's an "Extra Class"
    public int subjectId;
    public int startTime;
    public int endTime;

    @ColumnInfo(name = "subjectName")
    public String subjectName;

    // The JOIN query aliases this as 'classroomName'; @ColumnInfo maps it correctly
    @ColumnInfo(name = "classroomName")
    public String roomName;

    // classroomId comes from the JOIN on Timetable — Room maps it via @ColumnInfo
    @ColumnInfo(name = "classroomId")
    public Integer classroomId;

    // Status: null = Pending, 0 = Bunked, 1 = Present, 2 = Cancelled
    public Integer attendanceStatus;
}