package com.bunkmeter.app.ui.subject;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;

public class SubjectDetailActivity extends AppCompatActivity {

    private TextView tvSubjectName, tvAttendance;
    private RecyclerView rvGrid;
    private SubjectViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);

        int subjectId = getIntent().getIntExtra("subjectId", -1);
        String subjectName = getIntent().getStringExtra("subjectName");

        if (subjectId == -1) {
            finish();
            return;
        }

        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvAttendance = findViewById(R.id.tvAttendance);
        rvGrid = findViewById(R.id.rvAttendanceGrid);

        tvSubjectName.setText(subjectName);
        rvGrid.setLayoutManager(new GridLayoutManager(this, 7));

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(SubjectViewModel.class);
        viewModel.init(subjectId);

        // 2. Observe the LiveData
        viewModel.getAttendanceHistory().observe(this, attendanceList -> {
            // 3. Let the ViewModel do the heavy math
            SubjectViewModel.SubjectStats stats = viewModel.processAttendanceStats(attendanceList);

            // 4. Update UI
            tvAttendance.setText(stats.percentageText);
            rvGrid.setAdapter(new AttendanceGridAdapter(stats.gridData));
        });
    }
}