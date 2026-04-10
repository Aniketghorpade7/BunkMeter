package com.bunkmeter.app.ui.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.notifications.AttendanceNotificationHelper;
import com.bunkmeter.app.repository.ClassroomRepository;
import com.bunkmeter.app.repository.SubjectRepository;
import com.bunkmeter.app.repository.TimetableRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class AddEditLectureDialog extends Dialog {

    private Context context;
    private Timetable existing;
    private Runnable refresh;

    private Spinner spSubject, spDay, spClassroom;
    private Button btnStart, btnEnd, btnSave;
    private TextView tvTitle;

    private int startTime = 0, endTime = 0;

    private TimetableRepository timetableRepo;
    private SubjectRepository subjectRepo;
    private ClassroomRepository classroomRepo;

    private List<Subject> subjects;
    private List<Classroom> classrooms;

    private boolean isTemporary;
    private String targetDate;

    public AddEditLectureDialog(Context ctx, Timetable existing, boolean isTemporary, String targetDate, Runnable refresh) {
        super(ctx);
        this.context = ctx;
        this.existing = existing;
        this.isTemporary = isTemporary;
        this.targetDate = targetDate;
        this.refresh = refresh;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.dialog_add_edit_subject);

        timetableRepo = new TimetableRepository((Application) context.getApplicationContext());
        subjectRepo = new SubjectRepository((Application) context.getApplicationContext());
        classroomRepo = new ClassroomRepository((Application) context.getApplicationContext());

        spSubject = findViewById(R.id.spSubject);
        spDay = findViewById(R.id.spDay);
        spClassroom = findViewById(R.id.spClassroom);
        btnStart = findViewById(R.id.btnStart);
        btnEnd = findViewById(R.id.btnEnd);
        btnSave = findViewById(R.id.btnSave);
        tvTitle = findViewById(R.id.tvTitle); // Now it has an ID!
        Button btnDelete = findViewById(R.id.btnDelete);

        loadSpinners();

        btnStart.setOnClickListener(v -> pickTime(true));
        btnEnd.setOnClickListener(v -> pickTime(false));
        btnSave.setOnClickListener(v -> save());

        if (existing != null) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> deleteTimetable());
        }

        if (isTemporary) {
            spDay.setVisibility(View.GONE);
            findViewById(R.id.tvTitle).setVisibility(View.VISIBLE); // To be sure
            if (tvTitle != null) tvTitle.setText("Add Extra Lecture (Today)");
        }

        if (isTemporary) {
            spDay.setVisibility(View.GONE);
            // Hide the label too!
            View tvDayLabel = findViewById(R.id.tvDayLabel);
            if (tvDayLabel != null) tvDayLabel.setVisibility(View.GONE);

            if (tvTitle != null) tvTitle.setText("Add Extra Lecture (Today)");
        }

    }

    private void save() {
        if (subjects == null || subjects.isEmpty()) {
            Toast.makeText(context, "Add a subject first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (startTime == 0 || endTime == 0 || startTime >= endTime) {
            Toast.makeText(context, "Invalid time range", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            int subId = subjects.get(spSubject.getSelectedItemPosition()).getSubjectId();
            int classPos = spClassroom.getSelectedItemPosition();
            Integer roomId = (classPos > 0 && classrooms != null) ? classrooms.get(classPos - 1).getClassroomId() : null;

            if (isTemporary) {
                // SAVE AS ATTENDANCE (Option 1)
                Attendance extra = new Attendance();
                extra.setSubjectId(subId);
                extra.setClassroomId(roomId != null ? roomId : 0);
                extra.setDate(targetDate);
                extra.setStartTime(startTime);
                extra.setEndTime(endTime);
                extra.setStatus(2); // PENDING

                AppDatabase.getInstance(context).attendanceDao().insertAttendance(extra);
                com.bunkmeter.app.scheduler.NotificationScheduler.rescheduleTodaysScheduleNow(context);

                ((Activity) context).runOnUiThread(() -> {
                    dismiss();
                    refresh.run();
                });
            } else {
                // SAVE AS PERMANENT TIMETABLE
                int day = spDay.getSelectedItemPosition();
                insertPermanent(subId, day, roomId);
            }
        });
    }

    // ... Keep existing loadSpinners, pickTime, insertPermanent, deleteTimetable methods from previous message ...

    private void loadSpinners() {
        Executors.newSingleThreadExecutor().execute(() -> {
            subjects = subjectRepo.getAllSubjects();
            ((Activity) context).runOnUiThread(() -> {
                classroomRepo.getActiveClassrooms(activeClassrooms -> {
                    classrooms = activeClassrooms;
                    if (subjects != null) {
                        ArrayAdapter<String> subAdapter = new ArrayAdapter<>(context,
                                android.R.layout.simple_spinner_item,
                                subjects.stream().map(Subject::getName).toArray(String[]::new));
                        spSubject.setAdapter(subAdapter);
                    }
                    spDay.setAdapter(new ArrayAdapter<>(context,
                            android.R.layout.simple_spinner_item,
                            new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}));
                    List<String> roomNames = new ArrayList<>();
                    roomNames.add("None");
                    if (classrooms != null) {
                        for (Classroom c : classrooms) roomNames.add(c.getName());
                    }
                    spClassroom.setAdapter(new ArrayAdapter<>(context,
                            android.R.layout.simple_spinner_item, roomNames));

                    if (existing != null) {
                        for (int i = 0; i < subjects.size(); i++) {
                            if (subjects.get(i).getSubjectId() == existing.getSubjectId()) {
                                spSubject.setSelection(i);
                                break;
                            }
                        }
                        spDay.setSelection(existing.getDayOfWeek());
                        startTime = existing.getStartTime();
                        endTime = existing.getEndTime();
                        btnStart.setText(formatTime(startTime));
                        btnEnd.setText(formatTime(endTime));
                        btnSave.setText("Update");
                    }
                });
            });
        });
    }

    private void pickTime(boolean isStart) {
        new TimePickerDialog(context, (view, h, m) -> {
            int val = h * 60 + m;
            @SuppressLint("DefaultLocale") String timeString = String.format("%02d:%02d", h, m);
            if (isStart) { startTime = val; btnStart.setText(timeString); }
            else { endTime = val; btnEnd.setText(timeString); }
        }, 9, 0, true).show();
    }

    private void insertPermanent(int subjectId, int day, Integer classroomId) {
        Timetable t = new Timetable(subjectId, day, startTime, endTime, classroomId, "Lecture");
        if (existing != null) timetableRepo.delete(existing);
        timetableRepo.insert(t);
        ((Activity) context).runOnUiThread(() -> {
            dismiss();
            refresh.run();
            // Notify user to create a classroom if none was assigned to this timetable slot
            if (classroomId == null) {
                AttendanceNotificationHelper.triggerCreateClassroomNotification(context);
            }
        });
    }

    private void deleteTimetable() {
        Executors.newSingleThreadExecutor().execute(() -> {
            timetableRepo.delete(existing);
            ((Activity) context).runOnUiThread(() -> { dismiss(); refresh.run(); });
        });
    }

    private String formatTime(int min) { return String.format("%02d:%02d", min / 60, min % 60); }
}