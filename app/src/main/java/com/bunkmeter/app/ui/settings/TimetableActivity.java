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
import android.widget.RelativeLayout;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import java.util.concurrent.Executors;

public class TimetableActivity extends AppCompatActivity {

    private RelativeLayout timetableGrid;
    private RelativeLayout timetableTimeBar;
    private TextView emptyView;
    private FloatingActionButton fab;
    private Button btnAddSubject;

    private TimetableRepository timetableRepo;
    private SubjectRepository subjectRepo;
    private ClassroomRepository classroomRepo;

    private List<Timetable> timetableList = new ArrayList<>();
    private List<Subject> subjectList = new ArrayList<>();
    private List<Classroom> classroomList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

        // Initialize views
        timetableGrid = findViewById(R.id.timetableGrid);
        timetableTimeBar = findViewById(R.id.timetableTimeBar);
        emptyView = findViewById(R.id.txtEmpty);
        fab = findViewById(R.id.fabAdd);
        btnAddSubject = findViewById(R.id.btnListSubject);

        // Repositories
        timetableRepo = new TimetableRepository(getApplication());
        subjectRepo = new SubjectRepository(getApplication());
        classroomRepo = new ClassroomRepository(getApplication());


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
            subjectList = subjectRepo.getAllSubjects();
            classroomRepo.getActiveClassrooms(activeClassrooms -> {
                classroomList = activeClassrooms;
                if (classroomList == null) classroomList = new ArrayList<>();
                runOnUiThread(() -> {
                    if (timetableList.isEmpty()) {
                        emptyView.setVisibility(View.VISIBLE);
                        timetableGrid.setVisibility(View.GONE);
                        timetableTimeBar.setVisibility(View.GONE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                        timetableGrid.setVisibility(View.VISIBLE);
                        timetableTimeBar.setVisibility(View.VISIBLE);
                        drawGrid();
                    }
                });
            });
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String formatTime(int min) {
        int h = min / 60;
        int m = min % 60;
        String amPm = h < 12 ? "AM" : "PM";
        int displayH = h % 12;
        if (displayH == 0) displayH = 12;
        return String.format("%02d:%02d %s", displayH, m, amPm);
    }

    static class TimeBlock implements Comparable<TimeBlock> {
        int start, end;
        TimeBlock(int s, int e) { start = s; end = e; }
        @Override public int compareTo(TimeBlock o) {
            if (this.start == o.start) return this.end - o.end;
            return this.start - o.start;
        }
        @Override public boolean equals(Object obj) {
            if (!(obj instanceof TimeBlock)) return false;
            TimeBlock o = (TimeBlock) obj;
            return this.start == o.start && this.end == o.end;
        }
        @Override public int hashCode() { return start * 31 + end; }
    }

    private void drawGrid() {
        timetableGrid.removeAllViews();
        timetableTimeBar.removeAllViews();

        int timeColWidth = dpToPx(80);
        int dayColWidth = dpToPx(110);
        int rowHeight = dpToPx(100);
        int headerHeight = dpToPx(40);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        List<TimeBlock> blocks = new ArrayList<>();
        for (Timetable t : timetableList) {
             TimeBlock tb = new TimeBlock(t.getStartTime(), t.getEndTime());
             if (!blocks.contains(tb)) blocks.add(tb);
        }
        java.util.Collections.sort(blocks);

        TextView tvCorner = new TextView(this);
        tvCorner.setText("Time");
        tvCorner.setGravity(android.view.Gravity.CENTER);
        tvCorner.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCorner.setTextColor(0xFF888888);
        RelativeLayout.LayoutParams lpCorner = new RelativeLayout.LayoutParams(timeColWidth, headerHeight);
        timetableTimeBar.addView(tvCorner, lpCorner);

        for (int i = 0; i < blocks.size(); i++) {
            TimeBlock tb = blocks.get(i);
            int yPos = headerHeight + (i * rowHeight);
            
            TextView tvTime = new TextView(this);
            tvTime.setText(formatTime(tb.start) + "\n|\n" + formatTime(tb.end));
            tvTime.setTextSize(12);
            tvTime.setTextColor(0xFF888888);
            tvTime.setGravity(android.view.Gravity.CENTER);
            
            RelativeLayout.LayoutParams lpTime = new RelativeLayout.LayoutParams(timeColWidth, rowHeight);
            lpTime.topMargin = yPos;
            timetableTimeBar.addView(tvTime, lpTime);

            View hLine = new View(this);
            hLine.setBackgroundColor(0x11000000); 
            RelativeLayout.LayoutParams lpLine = new RelativeLayout.LayoutParams(
                    days.length * dayColWidth, dpToPx(1));
            lpLine.topMargin = yPos;
            timetableGrid.addView(hLine, lpLine);
        }

        for (int i = 0; i < days.length; i++) {
            int xPos = i * dayColWidth;
            
            View vLine = new View(this);
            vLine.setBackgroundColor(0x11000000);
            RelativeLayout.LayoutParams lpVLine = new RelativeLayout.LayoutParams(
                    dpToPx(1), headerHeight + blocks.size() * rowHeight);
            lpVLine.leftMargin = xPos;
            timetableGrid.addView(vLine, lpVLine);
            
            TextView tvDay = new TextView(this);
            tvDay.setText(days[i]);
            tvDay.setTextSize(14);
            tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDay.setTextColor(0xFF888888);
            tvDay.setGravity(android.view.Gravity.CENTER);
            
            RelativeLayout.LayoutParams lpDay = new RelativeLayout.LayoutParams(dayColWidth, headerHeight);
            lpDay.leftMargin = xPos;
            timetableGrid.addView(tvDay, lpDay);
        }

        for (Timetable t : timetableList) {
            TimeBlock tb = new TimeBlock(t.getStartTime(), t.getEndTime());
            int rowIndex = blocks.indexOf(tb);
            if (rowIndex == -1) continue;
            
            int topMargin = headerHeight + (rowIndex * rowHeight);
            int leftMargin = t.getDayOfWeek() * dayColWidth;
            
            View slot = getLayoutInflater().inflate(R.layout.item_timetable_slot, timetableGrid, false);
            RelativeLayout.LayoutParams lpSlot = new RelativeLayout.LayoutParams(dayColWidth - dpToPx(2), rowHeight - dpToPx(2));
            lpSlot.leftMargin = leftMargin + dpToPx(1);
            lpSlot.topMargin = topMargin + dpToPx(1);
            slot.setLayoutParams(lpSlot);
            
            TextView subjectName = slot.findViewById(R.id.subjectName);
            TextView classroomName = slot.findViewById(R.id.classroomName);
            TextView time = slot.findViewById(R.id.time);
            android.widget.LinearLayout slotBg = slot.findViewById(R.id.slotBackground);
            
            time.setVisibility(View.GONE); 
            
            Subject subject = null;
            if (subjectList != null) {
                for (Subject s : subjectList) {
                    if (s.getSubjectId() == t.getSubjectId()) {
                        subject = s;
                        break;
                    }
                }
            }
            if (subject != null) {
                subjectName.setText(subject.getName());
                try {
                    slotBg.setBackgroundColor(android.graphics.Color.parseColor(subject.getColor()));
                } catch (Exception ignored) {}
            }
            
            if (t.getClassroomId() != null && classroomList != null) {
                Classroom classroom = null;
                for (Classroom c : classroomList) {
                    if (c.getClassroomId() == (int)t.getClassroomId()) {
                        classroom = c;
                        break;
                    }
                }
                if (classroom != null) {
                    classroomName.setText(classroom.getName());
                    classroomName.setVisibility(View.VISIBLE);
                } else {
                    classroomName.setVisibility(View.GONE);
                }
            } else {
                classroomName.setVisibility(View.GONE);
            }
            
            slot.setOnClickListener(v -> {
                new AddEditLectureDialog(this, t, false, null, this::loadData).show();
            });
            
            timetableGrid.addView(slot);
        }
    }
}