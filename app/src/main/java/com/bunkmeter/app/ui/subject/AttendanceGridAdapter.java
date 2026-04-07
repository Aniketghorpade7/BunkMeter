package com.bunkmeter.app.ui.subject;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;

import java.util.List;

public class AttendanceGridAdapter extends RecyclerView.Adapter<AttendanceGridAdapter.ViewHolder> {

    private List<Integer> list;

    public AttendanceGridAdapter(List<Integer> list) {
        this.list = list;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int status = list.get(position);

        if (status == 1) {
            holder.view.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            holder.view.setBackgroundColor(Color.parseColor("#F44336"));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        View view;

        public ViewHolder(View itemView) {
            super(itemView);
            view = itemView.findViewById(R.id.viewStatus);
        }
    }
}