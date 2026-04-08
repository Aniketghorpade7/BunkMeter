package com.bunkmeter.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.repository.ClassroomRepository;
import com.bunkmeter.app.repository.SubjectRepository;
import com.bunkmeter.app.repository.TimetableRepository;
import com.bunkmeter.app.ui.settings.timetable.DayAdapter;
import com.bunkmeter.app.ui.settings.timetable.ManageSubjectsBottomSheet;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class TimetableActivity extends AppCompatActivity {

    private RecyclerView dayRecyclerView;
    private TextView emptyView;
    private FloatingActionButton fab;
    private Button btnAddSubject;

    private TimetableRepository timetableRepo;
    private SubjectRepository subjectRepo;
    private ClassroomRepository classroomRepo;

    private List<Timetable> timetableList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

        // Initialize views
        dayRecyclerView = findViewById(R.id.dayRecyclerView);
        emptyView = findViewById(R.id.txtEmpty);
        fab = findViewById(R.id.fabAdd);
        btnAddSubject = findViewById(R.id.btnListSubject);

        // Repositories
        timetableRepo = new TimetableRepository(getApplication());
        subjectRepo = new SubjectRepository(getApplication());
        classroomRepo = new ClassroomRepository(getApplication());

        // RecyclerView
        dayRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Load data
        loadData();

        // FAB (Add timetable)
        fab.setOnClickListener(v -> {
            new AddEditLectureDialog(this, null, false, null, this::loadData).show();
        });

        // Add Subject button
        btnAddSubject.setOnClickListener(v -> {
            new ManageSubjectsBottomSheet(this::loadData).show(getSupportFragmentManager(), "ManageSubjects");
        });

        // Back button (Custom Modern Header)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide(); // Hide the default action bar
        }

        // 1. Correct Toolbar Setup
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // 2. Fix for the status bar overlap (Apply it to the new AppBarLayout instead of the deleted header)
        View appBarLayout = findViewById(R.id.appBarLayout);
        if (appBarLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                // Add padding to the top so it doesn't hide behind the status bar
                v.setPadding(0, insets.top, 0, 0);
                return androidx.core.view.WindowInsetsCompat.CONSUMED;
            });
        }
    }


    private void loadData() {
        Executors.newSingleThreadExecutor().execute(() -> {
            timetableList = timetableRepo.getFullTimetable();

            runOnUiThread(() -> {

                if (timetableList.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    dayRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    dayRecyclerView.setVisibility(View.VISIBLE);
                }

                dayRecyclerView.setAdapter(
                        new DayAdapter(this, timetableList, this::loadData));
            });
        });
    }
}