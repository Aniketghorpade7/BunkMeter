package com.bunkmeter.app.ui.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.repository.ClassroomRepository;
import com.bunkmeter.app.repository.SubjectRepository;
import com.bunkmeter.app.repository.TimetableRepository;
import com.bunkmeter.app.ui.settings.timetable.ManageSubjectsBottomSheet;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            getSupportActionBar().hide();
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        View appBarLayout = findViewById(R.id.appBarLayout);
        if (appBarLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(appBarLayout, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
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

    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private String formatTime(int min) {
        int h = min / 60;
        int m = min % 60;
        String amPm = h < 12 ? "AM" : "PM";
        int displayH = h % 12;
        if (displayH == 0) displayH = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", displayH, m, amPm);
    }

    private void drawGrid() {
        timetableGrid.removeAllViews();
        timetableTimeBar.removeAllViews();

        if (timetableList.isEmpty()) return;

        // --- 1. Configuration & Scale Setup ---
        // 1 Minute of time = 1.5 dp on screen. This controls the height of everything.
        float density = getResources().getDisplayMetrics().density;
        float pixelsPerMinute = 1.5f * density;

        int timeColWidth = dpToPx(80);
        int dayColWidth = dpToPx(110);
        int headerHeight = dpToPx(40);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        // --- 2. Calculate the dynamic grid boundaries ---
        // Find the absolute earliest start time and latest end time in the whole week
        int minStartMin = 24 * 60;
        int maxEndMin = 0;
        for (Timetable t : timetableList) {
            if (t.getStartTime() < minStartMin) minStartMin = t.getStartTime();
            if (t.getEndTime() > maxEndMin) maxEndMin = t.getEndTime();
        }

        // Add padding: Start the grid 1 hour before earliest class, end 1 hour after latest
        int startHour = (minStartMin / 60) - 1;
        if (startHour < 0) startHour = 0;
        int endHour = (maxEndMin / 60) + 1;
        if (endHour > 24) endHour = 24;

        int totalMinutes = (endHour - startHour) * 60;
        int gridTotalPixelHeight = (int) (totalMinutes * pixelsPerMinute);

        // --- 3. Draw Corner Header ---
        TextView tvCorner = new TextView(this);
        tvCorner.setText("Time");
        tvCorner.setGravity(android.view.Gravity.CENTER);
        tvCorner.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCorner.setTextColor(0xFF888888);
        RelativeLayout.LayoutParams lpCorner = new RelativeLayout.LayoutParams(timeColWidth, headerHeight);
        timetableTimeBar.addView(tvCorner, lpCorner);

        // --- 4. Draw Y-Axis (Time Labels) & Horizontal Grid Lines ---
        for (int h = startHour; h <= endHour; h++) {
            // Calculate absolute Y position for this specific hour
            int yPos = headerHeight + (int) (((h - startHour) * 60) * pixelsPerMinute);

            // Draw Time Label
            TextView tvTime = new TextView(this);
            tvTime.setText(formatTime(h * 60));
            tvTime.setTextSize(12);
            tvTime.setTextColor(0xFF888888);
            tvTime.setGravity(android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP);

            RelativeLayout.LayoutParams lpTime = new RelativeLayout.LayoutParams(timeColWidth, RelativeLayout.LayoutParams.WRAP_CONTENT);
            // Shift up slightly so the text perfectly aligns with the line
            lpTime.topMargin = yPos - dpToPx(8);
            timetableTimeBar.addView(tvTime, lpTime);

            // Draw Horizontal Grid Line
            View hLine = new View(this);
            hLine.setBackgroundColor(0x1A000000); // Faint line
            RelativeLayout.LayoutParams lpLine = new RelativeLayout.LayoutParams(days.length * dayColWidth, dpToPx(1));
            lpLine.topMargin = yPos;
            timetableGrid.addView(hLine, lpLine);
        }

        // --- 5. Draw X-Axis (Day Labels) & Vertical Grid Lines ---
        for (int i = 0; i < days.length; i++) {
            int xPos = i * dayColWidth;

            // Draw Vertical Grid Line
            View vLine = new View(this);
            vLine.setBackgroundColor(0x1A000000);
            RelativeLayout.LayoutParams lpVLine = new RelativeLayout.LayoutParams(dpToPx(1), headerHeight + gridTotalPixelHeight);
            lpVLine.leftMargin = xPos;
            timetableGrid.addView(vLine, lpVLine);

            // Draw Day Header
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

        // --- 6. Plot the Lectures! (The Magic Part) ---
        int startOfDayMinutes = startHour * 60;

        for (Timetable t : timetableList) {
            int durationMinutes = t.getEndTime() - t.getStartTime();
            if (durationMinutes <= 0) continue; // Safety catch

            // Calculate exact pixel dimensions based on time
            int topMargin = headerHeight + (int) ((t.getStartTime() - startOfDayMinutes) * pixelsPerMinute);
            int height = (int) (durationMinutes * pixelsPerMinute);
            int leftMargin = t.getDayOfWeek() * dayColWidth;

            // Prepare layout params with a tiny bit of padding so it doesn't touch grid lines
            int padding = dpToPx(2);
            View slot = getLayoutInflater().inflate(R.layout.item_timetable_slot, timetableGrid, false);
            RelativeLayout.LayoutParams lpSlot = new RelativeLayout.LayoutParams(dayColWidth - (padding * 2), height - (padding * 2));
            lpSlot.leftMargin = leftMargin + padding;
            lpSlot.topMargin = topMargin + padding;
            slot.setLayoutParams(lpSlot);

            // Bind Views
            TextView subjectName = slot.findViewById(R.id.subjectName);
            TextView classroomName = slot.findViewById(R.id.classroomName);
            TextView time = slot.findViewById(R.id.time);
            android.widget.LinearLayout slotBg = slot.findViewById(R.id.slotBackground);

            // Show explicit time inside the tile since the grid is proportional now
            time.setText(formatTime(t.getStartTime()) + " - " + formatTime(t.getEndTime()));
            time.setVisibility(View.VISIBLE);

            // Attach Subject Data
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

            // Attach Classroom Data
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

            // Edit on click
            slot.setOnClickListener(v -> {
                new AddEditLectureDialog(this, t, false, null, this::loadData).show();
            });

            timetableGrid.addView(slot);
        }
    }
}