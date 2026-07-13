package com.bunkmeter.app.scheduler;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.receiver.GeofenceBroadcastReceiver;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers and removes Android OS geofences for the user's classrooms.
 *
 * <h3>How geofencing replaces the old polling approach</h3>
 * The old design ran {@code LocationReadingWorker} three times per lecture and
 * actively asked "where am I right now?". That failed indoors (GPS is useless
 * inside buildings) and was throttled by Doze mode.
 *
 * Geofencing flips the model: we hand the OS a list of circles ONCE, and the OS
 * watches the user's position for us — efficiently, using WiFi + cell + GPS
 * fused together — and calls {@link GeofenceBroadcastReceiver} the moment the
 * user enters one. No polling, no battery drain, no score system.
 *
 * <h3>Two facts that shape this class</h3>
 * <ol>
 *   <li><b>Geofences are wiped on reboot.</b> So {@link #registerAllClassrooms}
 *       is an idempotent "rebuild everything" call we trigger on app start,
 *       after any classroom edit, and after boot (see BootReceiver).</li>
 *   <li><b>The OS ignores very small geofences.</b> Below ~100&nbsp;m the system
 *       can't reliably detect a crossing, so we clamp the radius to a sensible
 *       floor regardless of what the user typed.</li>
 * </ol>
 */
public class GeofenceManager {

    private static final String TAG = "GeofenceManager";

    /**
     * Android cannot reliably trigger geofences smaller than ~100 m, so we use
     * this as a floor. The user's exact radius is still kept for the in-app
     * "Test classroom" distance check — this floor only applies to the OS
     * geofence used for automatic attendance.
     */
    private static final float MIN_GEOFENCE_RADIUS_METERS = 100f;

    /** Geofences never expire on their own — we manage their lifecycle manually. */
    private static final long EXPIRATION_NEVER = Geofence.NEVER_EXPIRE;

    /**
     * Rebuilds the full set of OS geofences from every active classroom in the DB.
     * Safe to call repeatedly — it removes the old set first, then adds the current one.
     *
     * @param context any context (we use the application context internally).
     */
    @SuppressLint("MissingPermission") // we check permissions explicitly below
    public static void registerAllClassrooms(Context context) {
        Context appContext = context.getApplicationContext();

        // 1) Geofencing needs background location on Android 10+ (API 29). If we
        //    don't have it, silently skip — the manual notification path still
        //    works, so the app degrades gracefully instead of crashing.
        if (!hasLocationPermissions(appContext)) {
            Log.w(TAG, "Skipping geofence registration: location permission not granted.");
            return;
        }

        // 2) Reading the DB is a disk operation, so do it off the main thread.
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Classroom> classrooms =
                    AppDatabase.getInstance(appContext).classroomDao().getActiveClassrooms();

            GeofencingClient client = LocationServices.getGeofencingClient(appContext);
            PendingIntent pendingIntent = getGeofencePendingIntent(appContext);

            // Always clear the existing set first so deletions/edits take effect.
            client.removeGeofences(pendingIntent);

            if (classrooms.isEmpty()) {
                Log.d(TAG, "No active classrooms — nothing to register.");
                return;
            }

            List<Geofence> geofences = new ArrayList<>();
            for (Classroom c : classrooms) {
                if (c.getClassroomId() == null) continue;

                float radius = Math.max(c.getRadius(), MIN_GEOFENCE_RADIUS_METERS);

                geofences.add(new Geofence.Builder()
                        // The request ID is how the receiver knows WHICH classroom
                        // was entered. We use the classroom's primary key.
                        .setRequestId(String.valueOf(c.getClassroomId()))
                        .setCircularRegion(c.getLatitude(), c.getLongitude(), radius)
                        .setExpirationDuration(EXPIRATION_NEVER)
                        // We only care about ENTER. (DWELL — "stayed inside N minutes"
                        // — would avoid false positives from walking past, but ENTER
                        // plus our lecture-time-window check is simpler and reliable.)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                        .build());
            }

            GeofencingRequest request = new GeofencingRequest.Builder()
                    // INITIAL_TRIGGER_ENTER: if the user is ALREADY inside a classroom
                    // at registration time, fire immediately (handy on app start).
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(geofences)
                    .build();

            try {
                client.addGeofences(request, pendingIntent)
                        .addOnSuccessListener(v ->
                                Log.d(TAG, "Registered " + geofences.size() + " geofence(s)."))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Failed to register geofences", e));
            } catch (SecurityException e) {
                // Permission was revoked between our check and this call.
                Log.e(TAG, "SecurityException registering geofences", e);
            }
        });
    }

    /** Removes every geofence this app registered. Used on full data reset. */
    public static void removeAllGeofences(Context context) {
        Context appContext = context.getApplicationContext();
        LocationServices.getGeofencingClient(appContext)
                .removeGeofences(getGeofencePendingIntent(appContext));
    }

    /**
     * Builds the PendingIntent the OS fires when a geofence transition happens.
     * It must be stable (same request code + same intent) so the OS recognises
     * it for both adding and removing geofences.
     */
    private static PendingIntent getGeofencePendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        intent.setAction(GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT);

        // FLAG_MUTABLE is REQUIRED for geofence PendingIntents — the OS needs to
        // write the triggering-geofence data into the intent when it fires.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /**
     * True only if we hold the permissions geofencing actually needs:
     * fine location everywhere, plus background location on Android 10+.
     */
    public static boolean hasLocationPermissions(Context context) {
        boolean fine = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean background = true; // not required below Android 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            background = ContextCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return fine && background;
    }
}
