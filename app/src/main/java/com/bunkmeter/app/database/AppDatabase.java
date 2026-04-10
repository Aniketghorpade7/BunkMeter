package com.bunkmeter.app.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.bunkmeter.app.database.AttendanceDao;
import com.bunkmeter.app.database.ClassroomDao;
import com.bunkmeter.app.database.SubjectDao;
import com.bunkmeter.app.database.TimetableDao;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Attendance.class,
                Subject.class,
                Classroom.class,
                Timetable.class
        },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract AttendanceDao attendanceDao();
    public abstract ClassroomDao classroomDao();
    public abstract SubjectDao subjectDao();
    public abstract TimetableDao timetableDao();

    public abstract ResetDao resetDao();

    // Singleton
    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "bunkmeter_db"
                            )
                            .addMigrations(MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE Attendance_new (" +
                "attendanceId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "subjectId INTEGER NOT NULL, " +
                "date TEXT, " +
                "startTime INTEGER NOT NULL, " +
                "endTime INTEGER NOT NULL, " +
                "status INTEGER NOT NULL, " +
                "classroomId INTEGER, " +
                "locationVerified INTEGER NOT NULL, " +
                "FOREIGN KEY(subjectId) REFERENCES Subject(subjectId) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(classroomId) REFERENCES Classroom(classroomId) ON UPDATE NO ACTION ON DELETE CASCADE)");

            database.execSQL("INSERT INTO Attendance_new (attendanceId, subjectId, date, startTime, endTime, status, classroomId, locationVerified) " +
                "SELECT attendanceId, subjectId, date, startTime, endTime, status, NULLIF(classroomId, 0), locationVerified FROM Attendance");

            database.execSQL("DROP TABLE Attendance");
            database.execSQL("ALTER TABLE Attendance_new RENAME TO Attendance");
        }
    };

    public static AppDatabase getInstance(Context context) {
        return getDatabase(context);
    }

    // Thread pool
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
}