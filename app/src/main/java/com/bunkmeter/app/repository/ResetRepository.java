package com.bunkmeter.app.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.database.ResetDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResetRepository {

    private final ResetDao resetDao;
    private final ExecutorService executorService;

    // Callback interface to communicate back to the UI
    public interface ResetCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public ResetRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        this.resetDao = db.resetDao();
        // Use a single thread executor to ensure operations happen sequentially
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void resetAppData(ResetCallback callback) {
        executorService.execute(() -> {
            try {
                // Must be executed in this exact order
                resetDao.deleteAttendance();
                resetDao.deleteTimetable();
                resetDao.deleteSubjects();
                resetDao.deleteClassrooms();

                // Post success back to the Main (UI) thread
                new Handler(Looper.getMainLooper()).post(callback::onSuccess);
            } catch (Exception e) {
                // Post error back to the Main (UI) thread
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e));
            }
        });
    }
}