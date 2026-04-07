package com.bunkmeter.app.ui.home;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class LectureAdapter extends RecyclerView.Adapter<LectureAdapter.ViewHolder> {

    private List<Timetable> timetableList;
    private String currentDate; // We need this to know what day we are logging!

    public LectureAdapter(List<Timetable> timetableList, String currentDate) {
        this.timetableList = timetableList;
        this.currentDate = currentDate;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubject, tvRoomName, tvTime, tvStatus;
        Button btnAttend, btnBunk, btnCancel;

        public ViewHolder(View view) {
            super(view);
            tvSubject = view.findViewById(R.id.tvSubject);
            tvRoomName = view.findViewById(R.id.tvRoomName);
            tvTime = view.findViewById(R.id.tvTime);
            tvStatus = view.findViewById(R.id.tvStatus);
            btnAttend = view.findViewById(R.id.btnAttend);
            btnBunk = view.findViewById(R.id.btnBunk); // New Button
            btnCancel = view.findViewById(R.id.btnCancel);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lecture, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Timetable currentClass = timetableList.get(position);

        // Format times
        String timeString = formatTime(currentClass.getStartTime()) + " - " + formatTime(currentClass.getEndTime());
        holder.tvTime.setText(timeString);

        // Default UI states while loading from DB
        holder.tvSubject.setText("Loading...");
        holder.tvRoomName.setText("...");
        holder.tvStatus.setText("PENDING");
        holder.tvStatus.setTextColor(Color.GRAY);

        // --- 1. LOAD CLASS DETAILS & CURRENT ATTENDANCE STATUS ---
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(holder.itemView.getContext().getApplicationContext());

            // Get Subject Name
            Subject subject = db.subjectDao().getSubjectById(currentClass.getSubjectId());
            String subjectName = (subject != null) ? subject.getName() : "Unknown Subject";

            // Get Room Name
            String roomName = "No Room Set";
            if (currentClass.getClassroomId() != null && currentClass.getClassroomId() != 0) {
                Classroom room = db.classroomDao().getClassroomById(currentClass.getClassroomId());
                if (room != null) roomName = room.getName();
            }

            // Check if user already marked attendance for this class today!
            Attendance existingRecord = db.attendanceDao().getSpecificAttendance(
                    currentClass.getSubjectId(), currentDate, currentClass.getStartTime()
            );

            final String finalRoomName = roomName;

            // Push UI updates back to main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                holder.tvSubject.setText(subjectName);
                holder.tvRoomName.setText(finalRoomName);

                if (existingRecord != null) {
                    updateStatusUI(holder, existingRecord.getStatus());
                }
            });
        });

        // --- 2. BUTTON CLICK LISTENERS ---
        holder.btnAttend.setOnClickListener(v -> saveAttendance(holder, currentClass, 1)); // 1 = Present
        holder.btnBunk.setOnClickListener(v -> saveAttendance(holder, currentClass, 0));   // 0 = Bunk
        holder.btnCancel.setOnClickListener(v -> saveAttendance(holder, currentClass, 2)); // 2 = Class Cancelled
    }

    private void saveAttendance(ViewHolder holder, Timetable currentClass, int statusValue) {
        // Update UI immediately for a snappy feel
        updateStatusUI(holder, statusValue);

        // Save to DB in background safely
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(holder.itemView.getContext().getApplicationContext());

                // Check if a record already exists
                Attendance existingRecord = db.attendanceDao().getSpecificAttendance(
                        currentClass.getSubjectId(), currentDate, currentClass.getStartTime()
                );

                if (existingRecord == null) {
                    // First time clicking -> INSERT
                    Attendance newRecord = new Attendance();
                    newRecord.setSubjectId(currentClass.getSubjectId());
                    newRecord.setDate(currentDate);
                    newRecord.setStartTime(currentClass.getStartTime());
                    newRecord.setEndTime(currentClass.getEndTime());
                    newRecord.setStatus(statusValue);

                    // NEW FIX: You must pass the Classroom ID from the Timetable so SQLite doesn't crash!
                    if (currentClass.getClassroomId() != null) {
                        newRecord.setClassroomId(currentClass.getClassroomId());
                    }

                    db.attendanceDao().insertAttendance(newRecord);
                } else {
                    // Changing mind -> UPDATE
                    existingRecord.setStatus(statusValue);
                    db.attendanceDao().updateAttendance(existingRecord);
                }

            } catch (Exception e) {
                e.printStackTrace();

                // If the database crashes, show the exact error in a Toast INSTEAD of closing the app!
                new Handler(Looper.getMainLooper()).post(() -> {
                    android.widget.Toast.makeText(holder.itemView.getContext(),
                            "DB Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();

                    // Reset the UI since the save failed
                    holder.tvStatus.setText("ERROR");
                    holder.tvStatus.setTextColor(android.graphics.Color.RED);
                });
            }
        });
    }

    // --- UI HELPER: Colors the text based on status ---
    private void updateStatusUI(ViewHolder holder, int statusValue) {
        if (statusValue == 1) {
            holder.tvStatus.setText("PRESENT");
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else if (statusValue == 0) {
            holder.tvStatus.setText("BUNKED");
            holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
        } else if (statusValue == 2) {
            holder.tvStatus.setText("CLASS CANCELLED");
            holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E")); // Grey
        }
    }

    // Helper method to format minutes into HH:MM
    private String formatTime(int totalMinutes) {
        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        String ampm = (hours >= 12) ? "PM" : "AM";
        if (hours > 12) hours -= 12;
        if (hours == 0) hours = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", hours, minutes, ampm);
    }

    @Override
    public int getItemCount() {
        return timetableList == null ? 0 : timetableList.size();
    }

    public void setTimetableList(List<Timetable> newList, String newDate) {
        this.timetableList = newList;
        this.currentDate = newDate;
        notifyDataSetChanged();
    }
}