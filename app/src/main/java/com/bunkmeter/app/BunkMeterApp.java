package com.bunkmeter.app;

import android.app.Application;
import com.google.android.material.color.DynamicColors;

public class BunkMeterApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // This is the magic line that enables Material You on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}