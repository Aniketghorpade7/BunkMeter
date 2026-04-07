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
public class Timetable
{
    @PrimaryKey(autoGenerate = true)
    private int timetableId;

    private int subjectId;

    private int dayOfWeek;

    private int startTime;
    private int endTime;

    private Integer classroomId;

    private String type;

    //Constructor
    public Timetable(int subjectId, int dayOfWeek,
                     int startTime, int endTime,
                     Integer classroomId, String type){
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroomId = classroomId;
        this.type = type;
    }

    public Timetable() {
        // Required empty constructor for Room and manual object creation
    }

    // getters
    public int getTimetableId() {
        return timetableId;
    }

    public int getSubjectId() {
        return subjectId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public Integer getClassroomId() {
        return classroomId;
    }

    public String getType() {
        return type;
    }

    //Setters
    public void setTimetableId(int timetableId) {
        this.timetableId = timetableId;
    }

    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public void setClassroomId(Integer classroomId) {
        this.classroomId = classroomId;
    }

    public void setType(String type) {
        this.type = type;
    }
}