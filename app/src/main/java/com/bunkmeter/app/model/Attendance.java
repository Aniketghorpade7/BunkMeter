package com.bunkmeter.app.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        foreignKeys = {
                @ForeignKey(
                        entity = Subject.class,
                        parentColumns = "subjectId",
                        childColumns = "subjectId",
                        onDelete = CASCADE
                ),
                @ForeignKey(
                        entity = Classroom.class,
                        parentColumns = "classroomId",
                        childColumns = "classroomId",
                        onDelete = CASCADE
                )
        }
)
public class Attendance
{
    // --- Status constants replaced by AttendanceStatus enum ---
    // Keep numeric aliases for backward-compatible SQL queries / adapters that
    // still compare against getStatus() int directly.
    /** @deprecated Use {@link AttendanceStatus#PRESENT} */
    @Deprecated
    public static final int PRESENT = AttendanceStatus.PRESENT.value;  // 1
    /** @deprecated Use {@link AttendanceStatus#BUNK} */
    @Deprecated
    public static final int ABSENT  = AttendanceStatus.BUNK.value;      // 0

    @PrimaryKey(autoGenerate = true)
    private int attendanceId;

    private int subjectId;

    private String date;
    private int startTime;
    private int endTime;

    private int status; // (0=absent, 1=present)

    private Integer classroomId;

    private boolean locationVerified;

    //Getters
    public int getAttendanceId() {
        return attendanceId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public String getDate() {
        return date;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public int getStatus() {
        return status;
    }

    public Integer getClassroomId() {
        return classroomId;
    }

    public boolean isLocationVerified() {
        return locationVerified;
    }

    //Setters
    public void setAttendanceId(int attendanceId) {
        this.attendanceId = attendanceId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    // --- Typed enum accessors (preferred for new code) ---

    /** Returns the attendance status as a strongly-typed {@link AttendanceStatus}. */
    public AttendanceStatus getAttendanceStatus() {
        return AttendanceStatus.fromInt(status);
    }

    /**
     * Sets the attendance status from a typed enum, translating it to the raw
     * integer that Room persists in the database.
     */
    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.status = attendanceStatus.value;
    }

    public void setClassroomId(Integer classroomId) {
        this.classroomId = classroomId;
    }

    public void setLocationVerified(boolean locationVerified) {
        this.locationVerified = locationVerified;
    }
}