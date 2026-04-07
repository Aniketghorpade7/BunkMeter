package com.bunkmeter.app.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Classroom {
    @PrimaryKey(autoGenerate = true)
    private Integer classroomId;
    private String name;
    private double latitude;
    private double longitude;
    private float radius; // Changed to float as distanceBetween returns float
    private boolean isActive = true; // Default to true

    // Getters
    public Integer getClassroomId() { return classroomId; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public float getRadius() { return radius; }
    public boolean isActive() { return isActive; }

    // Setters
    public void setClassroomId(Integer classroomId) { this.classroomId = classroomId; }
    public void setName(String name) { this.name = name; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setRadius(float radius) { this.radius = radius; }
    public void setActive(boolean active) { isActive = active; }
}