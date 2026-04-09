package com.bunkmeter.app.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.TimeUnit;

public class LocationHelper {

    @SuppressLint("MissingPermission")
    public static Location getFreshLocationSync(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            return null; // GPS is OFF
        }

        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

        try {
            // Block thread for max 5 seconds to get a highly accurate location
            return Tasks.await(
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken()),
                    5, TimeUnit.SECONDS
            );
        } catch (Exception e) {
            cancellationTokenSource.cancel();
            return null;
        }
    }

    public static boolean isMockLocation(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        } else {
            return location.isFromMockProvider();
        }
    }

    public static boolean isWithinRadius(Location userLocation, double classLat, double classLng, float radiusInMeters) {
        float[] results = new float[1];
        Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), classLat, classLng, results);
        float distance = results[0];
        return distance <= radiusInMeters;
    }
}