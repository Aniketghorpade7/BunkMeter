package com.bunkmeter.app.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;
import com.bunkmeter.app.model.Classroom;

@Dao
public interface ClassroomDao {
    @Insert
    void insert(Classroom classroom);

    @Update
    void update(Classroom classroom);

    @Query("SELECT * FROM Classroom WHERE isActive = 1")
    List<Classroom> getActiveClassrooms();

    @Query("SELECT * FROM Classroom WHERE classroomId = :id")
    Classroom getClassroomById(int id);

    // Soft delete
    @Query("UPDATE Classroom SET isActive = 0 WHERE classroomId = :id")
    void softDelete(int id);
}