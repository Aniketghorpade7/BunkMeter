package com.bunkmeter.app.scheduler;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.TempReadingStorage;
import com.bunkmeter.app.location.LocationHelper;

public class LocationReadingWorker extends Worker {

    public LocationReadingWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long lectureId = getInputData().getLong("lecture_id", -1);
        int readingIndex = getInputData().getInt("reading_index", -1);
        double classLat = getInputData().getDouble("class_lat", 0);
        double classLng = getInputData().getDouble("class_lng", 0);
        float radius = getInputData().getFloat("radius", 20f);

        if (lectureId == -1 || readingIndex == -1) return Result.failure();

        boolean isValid = false;

        Location location = LocationHelper.getFreshLocationSync(getApplicationContext());

        // Retry logic if null (maybe GPS was sleeping)
        if (location == null) {
            location = LocationHelper.getFreshLocationSync(getApplicationContext());
        }

        if (location != null && !LocationHelper.isMockLocation(location)) {
            isValid = LocationHelper.isWithinRadius(location, classLat, classLng, radius);
        }

        TempReadingStorage.saveReading(getApplicationContext(), lectureId, readingIndex, isValid);

        return Result.success();
    }
}