package com.bunkmeter.app.ui.settings.timetable;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.repository.SubjectRepository;
import com.bunkmeter.app.ui.settings.AddEditLectureDialog;

import java.util.List;
import java.util.concurrent.Executors;

public class TimetableAdapter extends RecyclerView.Adapter<TimetableAdapter.ViewHolder> {

    private Context context;
    private List<Timetable> list;
    private Runnable refresh;
    private SubjectRepository subjectRepo;
    private com.bunkmeter.app.repository.ClassroomRepository classroomRepo;

    public TimetableAdapter(Context context, List<Timetable> list, Runnable refresh) {
        this.context = context;
        this.list = list;
        this.refresh = refresh;
        // Initialize the repo to fetch colors and names
        this.subjectRepo = new SubjectRepository((Application) context.getApplicationContext());
        this.classroomRepo = new com.bunkmeter.app.repository.ClassroomRepository((Application) context.getApplicationContext());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_timetable_slot, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        Timetable t = list.get(i);
        h.time.setText(format(t.getStartTime()) + " - " + format(t.getEndTime()));

        // --- 1. CALENDAR MATH (Alignment, Size, and Spacing) ---
        // Assume the college day starts at 8:00 AM (480 minutes)
        int startOfDay = 8 * 60;

        // Calculate empty gap before this class
        int previousEndTime = (i == 0) ? startOfDay : list.get(i - 1).getEndTime();
        int gapInMinutes = t.getStartTime() - previousEndTime;
        int durationInMinutes = t.getEndTime() - t.getStartTime();

        // Convert minutes to physical screen pixels (1.5dp per minute looks great)
        float density = context.getResources().getDisplayMetrics().density;
        int topMarginPx = (int) (Math.max(0, gapInMinutes) * density * 1.5f);
        int heightPx = (int) (durationInMinutes * density * 1.5f);

        // Apply the dynamic height and spacing!
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) h.itemView.getLayoutParams();
        params.topMargin = topMarginPx;
        params.bottomMargin = (int) (4 * density); // tiny gap at bottom just in case
        params.height = heightPx;
        h.itemView.setLayoutParams(params);

        // --- 2. FETCH COLOR AND NAME ---
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Subject> allSubjects = subjectRepo.getAllSubjects();
            for (Subject s : allSubjects) {
                if (s.getSubjectId() == t.getSubjectId()) {
                    ((Activity) context).runOnUiThread(() -> {
                        h.subject.setText(s.getName());
                        try {
                            // Paint the box!
                            h.slotBackground.setBackgroundColor(Color.parseColor(s.getColor()));
                        } catch (Exception e) {
                            // Fallback if color string is invalid
                        }
                    });
                    break;
                }
            }
            if (t.getClassroomId() != null) {
                classroomRepo.getClassroomById(t.getClassroomId(), classroom -> {
                    if (classroom != null) {
                        h.classroomName.setText(classroom.getName());
                        h.classroomName.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                ((Activity) context).runOnUiThread(() -> {
                    h.classroomName.setVisibility(View.GONE);
                });
            }
        });

        h.itemView.setOnClickListener(v -> {
            new AddEditLectureDialog(context, t, false, null, refresh).show();
        });
    }

    private String format(int min) {
        int h = min / 60;
        int m = min % 60;
        String amPm = h < 12 ? "AM" : "PM";
        int displayH = h % 12;
        if (displayH == 0) displayH = 12;
        return String.format("%02d:%02d %s", displayH, m, amPm);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView subject, classroomName, time;
        LinearLayout slotBackground;

        public ViewHolder(View v) {
            super(v);
            subject = v.findViewById(R.id.subjectName);
            classroomName = v.findViewById(R.id.classroomName);
            time = v.findViewById(R.id.time);
            slotBackground = v.findViewById(R.id.slotBackground);
        }
    }
}