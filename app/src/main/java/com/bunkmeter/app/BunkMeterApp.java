package com.bunkmeter.app;

import android.app.Application;
import com.google.android.material.color.DynamicColors;
import com.bunkmeter.app.scheduler.GeofenceManager;
import com.bunkmeter.app.scheduler.NotificationScheduler;

public class BunkMeterApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Enables Material You dynamic colors on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this);
        // Kick off the daily 7 AM scheduling chain (KEEP so it doesn't reset on every restart)
        NotificationScheduler.scheduleDailySetupAt7AM(this);
        // Re-assert classroom geofences on every cold start. Geofences can be lost
        // (reboot, app force-stop, Play Services update), so registering here keeps
        // automatic attendance alive. GeofenceManager no-ops if permission is missing.
        GeofenceManager.registerAllClassrooms(this);
    }
}