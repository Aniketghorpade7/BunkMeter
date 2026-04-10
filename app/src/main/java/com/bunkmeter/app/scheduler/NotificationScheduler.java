package com.bunkmeter.app.scheduler;

import android.content.Context;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {

    public static final String TAG_TODAYS_SCHEDULE = "todays_schedule";

    public static void scheduleDailySetupAt7AM(Context context) {
        Calendar currentDate = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();

        targetDate.set(Calendar.HOUR_OF_DAY, 7);
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);

        if (targetDate.before(currentDate)) {
            targetDate.add(Calendar.HOUR_OF_DAY, 24); // Schedule for tomorrow if it's already past 7 AM
        }

        long timeDiff = targetDate.getTimeInMillis() - currentDate.getTimeInMillis();

        PeriodicWorkRequest dailyRequest = new PeriodicWorkRequest.Builder(DailySetupWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailySetupWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                dailyRequest
        );
    }

    /**
     * Event-Driven Trigger: Cancels all pending alarms for today's lectures,
     * and immediately runs the SetupWorker to parse the newly saved database
     * and schedule upcoming notifications.
     */
    public static void rescheduleTodaysScheduleNow(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        
        // Wipe existing delayed events
        workManager.cancelAllWorkByTag(TAG_TODAYS_SCHEDULE);
        
        // Immediately run DailySetupWorker to reload everything
        OneTimeWorkRequest refreshRequest = new OneTimeWorkRequest.Builder(DailySetupWorker.class).build();
        workManager.enqueue(refreshRequest);
    }
}