package com.bunkmeter.app.ui.subject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Subject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.ViewHolder> {

    private List<Subject> subjectList;

    // Use a Thread Pool instead of 'new Thread()' to prevent lag when scrolling!
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public SubjectAdapter(List<Subject> subjectList) {
        this.subjectList = subjectList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        Subject subject = subjectList.get(position);
        holder.subjectName.setText(subject.getName());

        // Set layout manager (GitHub style)
        holder.heatmapRecycler.setLayoutManager(
                new GridLayoutManager(
                        holder.itemView.getContext(),
                        5,
                        RecyclerView.HORIZONTAL,
                        false
                )
        );

        executor.execute(() -> {

            AppDatabase db = AppDatabase.getInstance(holder.itemView.getContext().getApplicationContext());

            // Get exact counts directly from the database! Much faster.
            int total = db.attendanceDao().getTotalClasses(subject.getSubjectId());
            int present = db.attendanceDao().getPresentClasses(subject.getSubjectId());

            float percentage = total == 0 ? 0 : (present * 100f) / total;

            // Fetch the full list just for the heatmap
            List<Attendance> list = db.attendanceDao().getAttendanceBySubject(subject.getSubjectId());

            // Sort by date for the heatmap
            Collections.sort(list, Comparator.comparing(Attendance::getDate));

            List<Integer> gridData = new ArrayList<>();
            for (Attendance a : list) {
                gridData.add(a.getStatus() == 1 ? 1 : -1);
            }

            // Send everything back to the UI thread
            holder.itemView.post(() -> {

                // 1. Update Percentage Text
                holder.tvPercentage.setText("Attendance: " + (int) percentage + "%");

                // 2. Update Heatmap Recycler
                holder.heatmapRecycler.setAdapter(new HeatmapAdapter(gridData));

                // 3. Clear month container (You can rebuild your month label logic here if needed)
                holder.monthContainer.removeAllViews();
            });
        });
    }
    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView subjectName, tvPercentage;
        RecyclerView heatmapRecycler;
        LinearLayout monthContainer;

        ViewHolder(View itemView) {
            super(itemView);

            subjectName = itemView.findViewById(R.id.subjectName);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
            heatmapRecycler = itemView.findViewById(R.id.heatmapRecycler);
            monthContainer = itemView.findViewById(R.id.monthContainer);
        }
    }

    public void updateSubjects(List<Subject> list) {
        this.subjectList = list;
        notifyDataSetChanged();
    }
}