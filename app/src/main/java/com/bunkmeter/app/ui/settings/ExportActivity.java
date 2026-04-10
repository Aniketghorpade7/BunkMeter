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
    private CheckBox cbIncludeCharts;
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
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadPreviewData() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        String name = prefs.getString("name", "Student");
        rollNo = prefs.getString("rollNo", "00");
        semester = prefs.getString("semester", "1");

        tvStudentInfo.setText(String.format("👤 Student: %s\n🎓 Semester: %s | Roll No: %s", name, semester, rollNo));

        Executors.newSingleThreadExecutor().execute(() -> {
            int totalSubjects = 6;
            int totalClasses = 142;
            int attendancePercent = 78;

            runOnUiThread(() -> {
                tvStatsSummary.setText(String.format(
                        "• Subjects Tracked: %d\n• Total Classes Logged: %d\n• Overall Attendance: %d%%",
                        totalSubjects, totalClasses, attendancePercent
                ));
            });
        });
    }

    private void startExportWorker() {
        progressOverlay.setVisibility(View.VISIBLE);
        btnOpenFile.setVisibility(View.GONE);
        progressBar.setIndeterminate(false);
        progressBar.setProgressCompat(0, true);

        Data inputData = new Data.Builder()
                .putString("timeframe", spinnerTimeframe.getSelectedItem().toString())
                .putBoolean("includeCharts", cbIncludeCharts.isChecked())
                .putBoolean("includeDaily", false)
                .putString("rollNo", rollNo)
                .putString("semester", semester)
                .build();

        OneTimeWorkRequest exportRequest = new OneTimeWorkRequest.Builder(ExportWorker.class)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance(this).enqueue(exportRequest);

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(exportRequest.getId())
                .observe(this, new Observer<WorkInfo>() {
                    @Override
                    public void onChanged(WorkInfo workInfo) {
                        if (workInfo != null) {
                            Data progress = workInfo.getProgress();
                            int currentProgress = progress.getInt("progress", 0);
                            String stateMessage = progress.getString("message");

                            if (stateMessage != null) {
                                tvProgressState.setText(stateMessage);
                            }
                            if (currentProgress > 0) {
                                progressBar.setProgressCompat(currentProgress, true);
                            }

                            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                                tvProgressState.setText("✅ Export Complete!");
                                generatedFileUri = workInfo.getOutputData().getString("fileUri");
                                btnOpenFile.setVisibility(View.VISIBLE);
                                progressBar.setProgressCompat(100, true);

                                // --- THIS IS THE FIX ---
                                // Only return to Settings automatically if the export succeeded AND we came from the Reset flow
                                boolean fromReset = getIntent().getBooleanExtra("fromReset", false);
                                if (fromReset) {
                                    Toast.makeText(ExportActivity.this, "Export finished! Proceeding to reset...", Toast.LENGTH_SHORT).show();
                                    Intent resultIntent = new Intent();
                                    resultIntent.putExtra("export_success", true);
                                    setResult(RESULT_OK, resultIntent);
                                    finish();
                                }
                                // ------------------------

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