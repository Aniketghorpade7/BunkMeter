package com.bunkmeter.app.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.database.ClassroomDao;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.scheduler.GeofenceManager;
import java.util.List;

public class ClassroomRepository {
    private final ClassroomDao classroomDao;
    private final Handler mainThreadHandler;
    private final Application application; // kept so we can refresh geofences

    public ClassroomRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        classroomDao = db.classroomDao();
        // Use the shared DB write pool (same as every other repository) instead of
        // a per-instance ExecutorService that was never shut down — that leaked a
        // thread on every Fragment recreation.
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public interface OnClassroomsLoadedListener { void onLoaded(List<Classroom> classrooms); }
    public interface OnClassroomLoadedListener { void onLoaded(Classroom classroom); }
    public interface OnOperationCompleteListener { void onComplete(); }

    public void getActiveClassrooms(OnClassroomsLoadedListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Classroom> classrooms = classroomDao.getActiveClassrooms();
            mainThreadHandler.post(() -> listener.onLoaded(classrooms));
        });
    }

    public void getClassroomById(int id, OnClassroomLoadedListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Classroom classroom = classroomDao.getClassroomById(id);
            mainThreadHandler.post(() -> listener.onLoaded(classroom));
        });
    }

    public void insert(Classroom classroom, OnOperationCompleteListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            classroomDao.insert(classroom);
            // A classroom changed → rebuild the OS geofence set so automatic
            // attendance reflects the new location immediately.
            GeofenceManager.registerAllClassrooms(application);
            mainThreadHandler.post(listener::onComplete);
        });
    }

    public void update(Classroom classroom, OnOperationCompleteListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            classroomDao.update(classroom);
            GeofenceManager.registerAllClassrooms(application);
            mainThreadHandler.post(listener::onComplete);
        });
    }

    public void softDelete(int id, OnOperationCompleteListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            classroomDao.softDelete(id);
            GeofenceManager.registerAllClassrooms(application);
            mainThreadHandler.post(listener::onComplete);
        });
    }
}