package com.bunkmeter.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bunkmeter.app.scheduler.GeofenceManager;

/**
 * Re-registers all classroom geofences after the device reboots.
 *
 * <h3>Why this is essential</h3>
 * The Android OS does NOT persist geofences across reboots. If we didn't
 * re-register them on boot, automatic attendance would silently stop working
 * after the user restarts their phone — and they'd never know why. This kind of
 * invisible failure is exactly what made the old GPS approach feel "useless".
 *
 * Registered in the manifest for {@code android.intent.action.BOOT_COMPLETED},
 * which requires the {@code RECEIVE_BOOT_COMPLETED} permission.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // GeofenceManager already checks permissions internally and degrades
            // gracefully if they're missing, so we can just call it.
            GeofenceManager.registerAllClassrooms(context);
        }
    }
}
