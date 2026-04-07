package com.bunkmeter.app.ui.settings.timetable;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Timetable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DayAdapter extends RecyclerView.Adapter<DayAdapter.DayViewHolder> {

    private Context context;
    private List<Timetable> fullList;
    private Runnable refresh;

    private String[] days = {"Mon","Tue","Wed","Thu","Fri","Sat"};

    public DayAdapter(Context context, List<Timetable> list, Runnable refresh) {
        this.context = context;
        this.fullList = list;
        this.refresh = refresh;
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_timetable_day, parent, false);
        return new DayViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {

        holder.dayTitle.setText(days[position]);

        List<Timetable> filtered = new ArrayList<>();
        for (Timetable t : fullList) {
            if (t.getDayOfWeek() == position) {
                filtered.add(t);
            }
        }

        Collections.sort(filtered, (a, b)->a.getStartTime()-b.getStartTime());

        holder.slotRecyclerView.setLayoutManager(
                new LinearLayoutManager(context));

        holder.slotRecyclerView.setAdapter(
                new TimetableAdapter(context, filtered, refresh));
    }

    @Override
    public int getItemCount() {
        return days.length;
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView dayTitle;
        RecyclerView slotRecyclerView;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayTitle = itemView.findViewById(R.id.dayTitle);
            slotRecyclerView = itemView.findViewById(R.id.slotRecyclerView);
        }
    }
}