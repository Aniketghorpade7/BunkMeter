package com.bunkmeter.app.model;

public class Lecture
{
    private String subjectName;
    private String time;
    private Status status;

    public enum Status{
        PENDING,
        ATTENDED,
        CANCELLED
    }

    public Lecture(String subjectName, String time){
        this.subjectName = subjectName;
        this.time = time;
        this.status = Status.PENDING;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public String getTime() {
        return time;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status){
        this.status = status;
    }
}
