package com.bunkmeter.app.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionUtils {

    public static final int LOCATION_REQUEST_CODE = 1001;
    public static final int BACKGROUND_LOCATION_REQUEST_CODE = 1002;
    public static final int NOTIFICATION_REQUEST_CODE = 1003;

    // Call this from MainActivity onCreate()
    public static void requestRequiredPermissions(Activity activity) {

        // 1. Ask for Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST_CODE);
            }
        }

        // 2. Ask for Foreground Location
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
        } else {
            // 3. If Foreground is already granted, check for Background (Android 10+)
            checkBackgroundLocation(activity);
        }
    }

    // Call this from MainActivity's onRequestPermissionsResult when Foreground is granted
    public static void checkBackgroundLocation(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                new AlertDialog.Builder(activity)
                        .setTitle("Background Location Needed")
                        .setMessage("To mark attendance automatically while your phone is locked, please select 'Allow all the time' on the next screen.")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            ActivityCompat.requestPermissions(activity,
                                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                    BACKGROUND_LOCATION_REQUEST_CODE);
                        })
                        .setNegativeButton("No thanks", null)
                        .setCancelable(false)
                        .show();
            }
        }
    }
}