package com.bunkmeter.app.ui.subject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
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
        android.content.Context ctx = holder.view.getContext();

        if (status == 1) {
            holder.view.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_present));
        } else {
            holder.view.setBackgroundColor(ContextCompat.getColor(ctx, R.color.status_bunk));
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