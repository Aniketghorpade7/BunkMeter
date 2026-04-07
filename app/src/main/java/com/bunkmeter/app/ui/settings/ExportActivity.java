package com.bunkmeter.app.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.bunkmeter.app.R;
import com.bunkmeter.app.ui.settings.export.ExportWorker;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.concurrent.Executors;

public class ExportActivity extends AppCompatActivity {

    private TextView tvStudentInfo, tvStatsSummary, tvProgressState;
    private Spinner spinnerTimeframe;
    private CheckBox cbIncludeCharts, cbIncludeDaily;
    private MaterialButton btnGenerate;
    private FrameLayout progressOverlay;
    private CircularProgressIndicator progressBar;
    private Button btnOpenFile;

    private String rollNo = "Unknown";
    private String semester = "Unknown";
    private String generatedFileUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        initViews();
        setupToolbar();
        loadPreviewData();

        btnGenerate.setOnClickListener(v -> startExportWorker());

        btnOpenFile.setOnClickListener(v -> {
            if (generatedFileUri != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(generatedFileUri), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "No app found to open Excel files.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void initViews() {
        tvStudentInfo = findViewById(R.id.tvStudentInfo);
        tvStatsSummary = findViewById(R.id.tvStatsSummary);
        spinnerTimeframe = findViewById(R.id.spinnerTimeframe);
        cbIncludeCharts = findViewById(R.id.cbIncludeCharts);
        cbIncludeDaily = findViewById(R.id.cbIncludeDaily);
        btnGenerate = findViewById(R.id.btnGenerate);
        progressOverlay = findViewById(R.id.progressOverlay);
        progressBar = findViewById(R.id.progressBar);
        tvProgressState = findViewById(R.id.tvProgressState);
        btnOpenFile = findViewById(R.id.btnOpenFile);

        String[] options = {"All Time", "This Month", "Last 7 Days"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeframe.setAdapter(adapter);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // This gives you the clean Pixel back arrow behavior
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadPreviewData() {
        // 1. Fetch from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        String name = prefs.getString("name", "Student");
        rollNo = prefs.getString("rollNo", "00");
        semester = prefs.getString("semester", "1");

        tvStudentInfo.setText(String.format("👤 Student: %s\n🎓 Semester: %s | Roll No: %s", name, semester, rollNo));

        // 2. Fetch database stats in background (Mocked here, replace with your actual Repository calls)
        Executors.newSingleThreadExecutor().execute(() -> {
            // TODO: Call your SubjectRepository / AttendanceRepository to get real counts
            int totalSubjects = 6; // Example
            int totalClasses = 142; // Example
            int attendancePercent = 78; // Example

            runOnUiThread(() -> {
                tvStatsSummary.setText(String.format(
                        "• Subjects Tracked: %d\n• Total Classes Logged: %d\n• Overall Attendance: %d%%",
                        totalSubjects, totalClasses, attendancePercent
                ));
            });
        });
    }

    private void startExportWorker() {
        // Show progress overlay
        progressOverlay.setVisibility(View.VISIBLE);
        btnOpenFile.setVisibility(View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.setProgressCompat(0, true);

        // Prepare data to send to Worker
        Data inputData = new Data.Builder()
                .putString("timeframe", spinnerTimeframe.getSelectedItem().toString())
                .putBoolean("includeCharts", cbIncludeCharts.isChecked())
                .putBoolean("includeDaily", cbIncludeDaily.isChecked())
                .putString("rollNo", rollNo)
                .putString("semester", semester)
                .build();

        // Create Work Request
        OneTimeWorkRequest exportRequest = new OneTimeWorkRequest.Builder(ExportWorker.class)
                .setInputData(inputData)
                .build();

        // Start Work
        WorkManager.getInstance(this).enqueue(exportRequest);

        // Observe Work Status (this survives app minimizing!)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(exportRequest.getId())
                .observe(this, new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null) {
                            // Update Progress
                            Data progress = workInfo.getProgress();
                            int currentProgress = progress.getInt("progress", 0);
                            String stateMessage = progress.getString("message");

                            if (stateMessage != null) {
                                tvProgressState.setText(stateMessage);
                            }
                            if (currentProgress > 0) {
                                progressBar.setProgressCompat(currentProgress, true);
                            }

                            // Handle Completion States
                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                tvProgressState.setText("✅ Export Complete!");
                                generatedFileUri = workInfo.getOutputData().getString("fileUri");
                                btnOpenFile.setVisibility(View.VISIBLE);
                                progressBar.setProgressCompat(100, true);
                            } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                                tvProgressState.setText("❌ Export Failed. Try again.");
                                btnOpenFile.setVisibility(View.GONE);
                                progressBar.setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }
}