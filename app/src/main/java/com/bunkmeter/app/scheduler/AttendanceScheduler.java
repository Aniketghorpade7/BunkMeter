package com.bunkmeter.app.scheduler;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class AttendanceScheduler {

    public static void scheduleAttendanceCheck(Context context, long lectureId, double classLat, double classLng, float radiusMeters) {

        WorkManager workManager = WorkManager.getInstance(context);

        Data.Builder dataBuilder = new Data.Builder()
                .putLong("lecture_id", lectureId)
                .putDouble("class_lat", classLat)
                .putDouble("class_lng", classLng)
                .putFloat("radius", radiusMeters);

        OneTimeWorkRequest reading1 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(10, TimeUnit.MINUTES)
                .setInputData(dataBuilder.putInt("reading_index", 1).build())
                .build();

        OneTimeWorkRequest reading2 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setInputData(dataBuilder.putInt("reading_index", 2).build())
                .build();

        OneTimeWorkRequest reading3 = new OneTimeWorkRequest.Builder(LocationReadingWorker.class)
                .setInitialDelay(20, TimeUnit.MINUTES)
                .setInputData(dataBuilder.putInt("reading_index", 3).build())
                .build();

        OneTimeWorkRequest evaluation = new OneTimeWorkRequest.Builder(EvaluationWorker.class)
                .setInitialDelay(22, TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putLong("lecture_id", lectureId).build())
                .build();

        // Enqueue uniquely based on lectureId to prevent duplicate overlapping workers
        workManager.enqueueUniqueWork("read1_" + lectureId, ExistingWorkPolicy.REPLACE, reading1);
        workManager.enqueueUniqueWork("read2_" + lectureId, ExistingWorkPolicy.REPLACE, reading2);
        workManager.enqueueUniqueWork("read3_" + lectureId, ExistingWorkPolicy.REPLACE, reading3);
        workManager.enqueueUniqueWork("eval_" + lectureId, ExistingWorkPolicy.REPLACE, evaluation);
    }
}