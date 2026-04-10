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
                        6,
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
            if (!list.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    
                    Map<String, Integer> attendanceMap = new HashMap<>();
                    for (Attendance a : list) {
                        attendanceMap.put(a.getDate(), a.getStatus());
                    }

                    Date firstDate = sdf.parse(list.get(0).getDate());
                    
                    Calendar startCal = Calendar.getInstance();
                    startCal.setTime(firstDate);
                    while (startCal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                        startCal.add(Calendar.DATE, -1);
                    }

                    Calendar endCal = Calendar.getInstance(); // Current date limits the ending to this week visually gracefully!
                    while (endCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
                        endCal.add(Calendar.DATE, 1);
                    }

                    Calendar currentCal = (Calendar) startCal.clone();

                    while (!currentCal.after(endCal)) {
                        int dayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK);
                        if (dayOfWeek != Calendar.SUNDAY) {
                            String dateStr = sdf.format(currentCal.getTime());
                            if (attendanceMap.containsKey(dateStr)) {
                                int status = attendanceMap.get(dateStr);
                                if (status == 1) gridData.add(1);
                                else if (status == 0) gridData.add(-1);
                                else gridData.add(0);
                            } else {
                                gridData.add(0);
                            }
                        }
                        currentCal.add(Calendar.DATE, 1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // Send everything back to the UI thread
            holder.itemView.post(() -> {

                // 1. Update Percentage Text
                holder.tvPercentage.setText("Attendance: " + (int) percentage + "%");

                // 2. Update Heatmap Recycler
                holder.heatmapRecycler.setAdapter(new HeatmapAdapter(gridData));

                // Setup pie chart
                holder.pieChart.getDescription().setEnabled(false);
                holder.pieChart.getLegend().setEnabled(false);
                holder.pieChart.setHoleRadius(40f);
                holder.pieChart.setTransparentCircleRadius(0f);
                holder.pieChart.setDrawEntryLabels(false);
                holder.pieChart.setTouchEnabled(false);
                
                if (total > 0) {
                    List<com.github.mikephil.charting.data.PieEntry> pieEntries = new ArrayList<>();
                    pieEntries.add(new com.github.mikephil.charting.data.PieEntry((float)present, "Present"));
                    pieEntries.add(new com.github.mikephil.charting.data.PieEntry((float)(total-present), "Absent"));
                    com.github.mikephil.charting.data.PieDataSet dataSet = new com.github.mikephil.charting.data.PieDataSet(pieEntries, "");
                    int colorPresent = android.graphics.Color.parseColor("#4CAF50");
                    int colorAbsent = android.graphics.Color.parseColor("#F44336");
                    dataSet.setColors(colorPresent, colorAbsent);
                    dataSet.setDrawValues(false); 
                    holder.pieChart.setData(new com.github.mikephil.charting.data.PieData(dataSet));
                    holder.pieChart.setVisibility(View.VISIBLE);
                    holder.pieChart.invalidate();
                } else {
                    holder.pieChart.setVisibility(View.INVISIBLE);
                    holder.pieChart.clear();
                }

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
        com.github.mikephil.charting.charts.PieChart pieChart;

        ViewHolder(View itemView) {
            super(itemView);

            subjectName = itemView.findViewById(R.id.subjectName);
            tvPercentage = itemView.findViewById(R.id.tvPercentage);
            heatmapRecycler = itemView.findViewById(R.id.heatmapRecycler);
            monthContainer = itemView.findViewById(R.id.monthContainer);
            pieChart = itemView.findViewById(R.id.pieChart);
        }
    }

    public void updateSubjects(List<Subject> list) {
        this.subjectList = list;
        notifyDataSetChanged();
    }
}