package com.bunkmeter.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Subject
{
    @PrimaryKey(autoGenerate = true)
    private int subjectId;

    private String name;

    private String color;

    public Subject(String name, String color) {
        this.name = name;
        this.color = color;
    }

    //Getters
    public int getSubjectId() {
        return subjectId;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    //Setters
    public void setSubjectId(int subjectId) {
        this.subjectId = subjectId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setColor(String color) {
        this.color = color;
    }
}