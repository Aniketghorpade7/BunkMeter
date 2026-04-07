package com.bunkmeter.app.repository;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.database.ClassroomDao;
import com.bunkmeter.app.model.Classroom;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClassroomRepository {
    private final ClassroomDao classroomDao;
    private final ExecutorService executor;
    private final Handler mainThreadHandler;

    public ClassroomRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        classroomDao = db.classroomDao();
        executor = Executors.newSingleThreadExecutor();
        mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public interface OnClassroomsLoadedListener { void onLoaded(List<Classroom> classrooms); }
    public interface OnClassroomLoadedListener { void onLoaded(Classroom classroom); }
    public interface OnOperationCompleteListener { void onComplete(); }

    public void getActiveClassrooms(OnClassroomsLoadedListener listener) {
        executor.execute(() -> {
            List<Classroom> classrooms = classroomDao.getActiveClassrooms();
            mainThreadHandler.post(() -> listener.onLoaded(classrooms));
        });
    }

    public void getClassroomById(int id, OnClassroomLoadedListener listener) {
        executor.execute(() -> {
            Classroom classroom = classroomDao.getClassroomById(id);
            mainThreadHandler.post(() -> listener.onLoaded(classroom));
        });
    }

    public void insert(Classroom classroom, OnOperationCompleteListener listener) {
        executor.execute(() -> {
            classroomDao.insert(classroom);
            mainThreadHandler.post(listener::onComplete);
        });
    }

    public void update(Classroom classroom, OnOperationCompleteListener listener) {
        executor.execute(() -> {
            classroomDao.update(classroom);
            mainThreadHandler.post(listener::onComplete);
        });
    }

    public void softDelete(int id, OnOperationCompleteListener listener) {
        executor.execute(() -> {
            classroomDao.softDelete(id);
            mainThreadHandler.post(listener::onComplete);
        });
    }
}