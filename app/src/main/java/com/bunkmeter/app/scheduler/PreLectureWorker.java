package com.bunkmeter.app.scheduler;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Subject;

public class PreLectureWorker extends Worker {

    public PreLectureWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        int subjectId = getInputData().getInt("subject_id", -1);

        if (subjectId == -1) {
            return Result.failure();
        }

        // Fetch the subject name synchronously from the DB
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        Subject subject = db.subjectDao().getSubjectById(subjectId);

        String subjectName = (subject != null) ? subject.getName() : "your next class";

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "pre_lecture_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Lecture Alerts",
                    NotificationManager.IMPORTANCE_HIGH // High importance so it pops up on screen
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Change to your app's notification icon
                .setContentTitle("Class starting soon!")
                .setContentText("Lecture starting in 10 minutes: " + subjectName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Using subjectId as the notification ID so multiple back-to-back classes don't overwrite each other
        notificationManager.notify(subjectId + 2000, builder.build());

        return Result.success();
    }
}