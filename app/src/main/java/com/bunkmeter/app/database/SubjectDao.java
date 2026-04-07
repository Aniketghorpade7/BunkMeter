package com.bunkmeter.app.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.bunkmeter.app.model.Subject;

import java.util.List;

@Dao
public interface SubjectDao
{
    @Insert
    long insertSubject(Subject subject);

    @Update
    void updateSubject(Subject subject);

    @Delete
    void deleteSubject(Subject subject);

    @Query("SELECT * FROM Subject ORDER BY name ASC")
    List<Subject> getAllSubjects();

    @Query("SELECT * FROM Subject WHERE subjectId = :id")
    Subject getSubjectById(int id);

    @Query("SELECT COUNT(*) FROM Subject")
    int getSubjectCount();
}