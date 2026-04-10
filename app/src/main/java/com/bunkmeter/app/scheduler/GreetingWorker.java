package com.bunkmeter.app.scheduler;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class GreetingWorker extends Worker {

    public GreetingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Grab the lecture count passed from DailySetupWorker
        int lectureCount = getInputData().getInt("lecture_count", 0);

        String message;
        if (lectureCount > 0) {
            message = "Good morning! You have " + lectureCount + " lectures today.";
        } else {
            message = "Good morning! You have no lectures today. Enjoy your day off! 🎮😴";
        }

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "daily_greeting_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Daily Greetings",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Change to your app's notification icon
                .setContentTitle("BunkMeter")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // Fixed ID so the greeting overwrites itself instead of spamming multiple notifications
        notificationManager.notify(1001, builder.build());

        return Result.success();
    }
}