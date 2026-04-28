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

/**
 * Thin wrapper around {@link FusedLocationProviderClient}.
 *
 * <p><strong>Design decision — no more Tasks.await():</strong><br>
 * The old synchronous {@code getFreshLocationSync()} blocked the calling thread
 * for up to 5 seconds.  When called from a WorkManager {@code Worker} that
 * runs on a shared thread-pool this can cause thread starvation.  We now expose
 * a pure-callback API and leave the threading strategy entirely to the caller
 * (see {@link com.bunkmeter.app.scheduler.LocationReadingWorker} which uses
 * {@code ListenableWorker + CallbackToFutureAdapter}).</p>
 */
public class LocationHelper {

    /**
     * Callback interface delivered back to the caller once the OS resolves
     * (or fails to resolve) the current location.
     */
    public interface OnLocationResult {
        /**
         * @param location The fresh location, or {@code null} if GPS is disabled
         *                 or the hardware returned no fix within the timeout.
         */
        void onResult(Location location);
    }

    /**
     * Requests a single high-accuracy location fix asynchronously.
     *
     * <p>The callback is guaranteed to be called exactly once on the main
     * thread (Fused Location's default executor).  Callers that need a
     * specific thread must hand off themselves.</p>
     *
     * @param context  Application or activity context.
     * @param callback Receives the location (possibly {@code null}) when ready.
     */
    @SuppressLint("MissingPermission")
    public static void getFreshLocation(Context context, OnLocationResult callback) {
        // Short-circuit when location hardware is completely disabled
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null
                || (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                &&  !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            callback.onResult(null);
            return;
        }

        FusedLocationProviderClient fusedClient =
                LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cts = new CancellationTokenSource();

        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(location -> callback.onResult(location))
                    .addOnFailureListener(e -> {
                        cts.cancel();
                        callback.onResult(null);
                    });
        } catch (SecurityException e) {
            callback.onResult(null); // Permission was revoked! Fail gracefully.
        }
    }

    // -------------------------------------------------------------------------
    // Utility methods (unchanged)
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the location was produced by a mock/fake provider. */
    public static boolean isMockLocation(Location location) {
        if (location == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        } else {
            return location.isFromMockProvider();
        }
    }

    /**
     * Returns {@code true} if {@code userLocation} falls within
     * {@code radiusInMeters} of the given classroom coordinates.
     */
    public static boolean isWithinRadius(Location userLocation,
                                         double classLat,
                                         double classLng,
                                         float radiusInMeters) {
        float[] results = new float[1];
        Location.distanceBetween(
                userLocation.getLatitude(), userLocation.getLongitude(),
                classLat, classLng,
                results);
        return results[0] <= radiusInMeters;
    }
}