package com.bunkmeter.app.ui.subject;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubjectDetailActivity extends AppCompatActivity {

    private TextView tvSubjectName, tvAttendance;
    private RecyclerView rvGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);

        int subjectId = getIntent().getIntExtra("subjectId", -1);

        if (subjectId == -1) {
            finish();
            return;
        }

        String subjectName = getIntent().getStringExtra("subjectName");

        tvSubjectName = findViewById(R.id.tvSubjectName);
        tvAttendance = findViewById(R.id.tvAttendance);
        rvGrid = findViewById(R.id.rvAttendanceGrid);

        tvSubjectName.setText(subjectName);

        rvGrid.setLayoutManager(new GridLayoutManager(this, 7));

        // FETCH DATA FROM DB
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<Attendance> list =
                    db.attendanceDao().getAttendanceBySubject(subjectId);

            //Testing purpose code
            if (list == null || list.isEmpty()) {

                // 🔥 Insert dummy attendance data
                for (int i = 1; i <= 20; i++) {
                    Attendance a = new Attendance();
                    a.setSubjectId(subjectId);

                    // fake dates (simple sequence)
                    a.setDate("2024-03-" + (i < 10 ? "0" + i : i));

                    // random present/absent
                    if (i % 3 == 0) {
                        a.setStatus(Attendance.ABSENT);
                    } else {
                        a.setStatus(Attendance.PRESENT);
                    }

                    db.attendanceDao().insertAttendance(a);
                }

                // fetch again
                list = db.attendanceDao().getAttendanceBySubject(subjectId);
            }
            //end of texting code

            //STEP 1: Group by date
            Map<String, Integer> dailyMap = new LinkedHashMap<>();

            for (Attendance a : list) {
                String date = a.getDate();

                if (!dailyMap.containsKey(date)) {
                    dailyMap.put(date, 0);
                }

                if (a.getStatus() == Attendance.PRESENT) {
                    dailyMap.put(date, dailyMap.get(date) + 1);
                }
            }

            //STEP 2: Convert to grid data
            List<Integer> gridData = new ArrayList<>();

            int presentDays = 0;

            for (String date : dailyMap.keySet()) {
                int presentCount = dailyMap.get(date);

                if (presentCount > 0) {
                    gridData.add(1); // present → green
                    presentDays++;
                } else {
                    gridData.add(0); // absent → red
                }
            }

            //STEP 3: Calculate percentage (DAY BASED)
            int totalDays = dailyMap.size();

            float percentage = totalDays == 0 ? 0 :
                    (presentDays * 100f) / totalDays;

            runOnUiThread(() -> {
                tvAttendance.setText("Attendance: " + (int) percentage + "%");
                rvGrid.setAdapter(new AttendanceGridAdapter(gridData));
            });

        }).start();
    }
}