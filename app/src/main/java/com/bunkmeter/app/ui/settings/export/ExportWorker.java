package com.bunkmeter.app.ui.settings.export;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Subject;

import java.io.File;
import java.util.List;

public class ExportWorker extends Worker {

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            reportProgress(10, "Fetching data from database...");

            String rollNo = getInputData().getString("rollNo");
            String sem = getInputData().getString("semester");
            boolean includeCharts = getInputData().getBoolean("includeCharts", false);
            boolean includeDaily = getInputData().getBoolean("includeDaily", false);

            // Fetch actual data using synchronous DAO calls
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            List<Subject> subjects = db.subjectDao().getAllSubjects();
            List<Attendance> attendances = db.attendanceDao().getAllAttendanceSync();

            if (attendances.isEmpty()) {
                reportProgress(100, "No data to export.");
                return Result.failure();
            }

            reportProgress(40, "Writing Excel Sheets...");

            // Define Output File in the public Downloads directory
            // We use System.currentTimeMillis() to prevent overwriting old reports if you want multiple versions
            String fileName = "BunkMeter_" + rollNo + "_Sem" + sem + "_" + System.currentTimeMillis() + ".xlsx";
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Ensure the directory exists (though Downloads usually always does)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            File outputFile = new File(downloadDir, fileName);

            // Generate the Excel File
            ExcelGenerator generator = new ExcelGenerator();
            generator.generateReport(outputFile, subjects, attendances, includeCharts, includeDaily);

            reportProgress(90, "Finalizing file...");

            // --- CRITICAL FIX: Media Scanner ---
            // This tells Android to refresh the Downloads folder so the file is visible immediately
            MediaScannerConnection.scanFile(getApplicationContext(),
                    new String[]{outputFile.getAbsolutePath()},
                    new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                    null);

            // Return success with the file URI so the UI can open it
            Data outputData = new Data.Builder()
                    .putString("fileUri", Uri.fromFile(outputFile).toString())
                    .build();

            return Result.success(outputData);

        } catch (Exception e) {
            e.printStackTrace();

            // Send the exact error message to the UI!
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown Error";
            Data errorData = new Data.Builder()
                    .putInt("progress", 100)
                    .putString("message", "❌ Error: " + errorMsg)
                    .build();
            setProgressAsync(errorData);

            return Result.failure();
        }
    }

    private void reportProgress(int percent, String message) {
        Data progressData = new Data.Builder()
                .putInt("progress", percent)
                .putString("message", message)
                .build();
        setProgressAsync(progressData);
    }
}