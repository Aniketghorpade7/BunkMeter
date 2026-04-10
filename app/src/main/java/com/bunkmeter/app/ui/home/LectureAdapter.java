package com.bunkmeter.app.ui.home;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bunkmeter.app.R;
import com.bunkmeter.app.model.HomeLectureItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LectureAdapter extends RecyclerView.Adapter<LectureAdapter.ViewHolder> {

    private List<HomeLectureItem> lectureList = new ArrayList<>();
    private final OnAttendanceClickListener clickListener;

    public interface OnAttendanceClickListener {
        void onActionClick(HomeLectureItem item, int statusValue);
    }

    public LectureAdapter(OnAttendanceClickListener clickListener) {
        this.clickListener = clickListener;
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
            btnBunk = view.findViewById(R.id.btnBunk);
            btnCancel = view.findViewById(R.id.btnCancel);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lecture, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeLectureItem item = lectureList.get(position);

        String timeString = formatTime(item.startTime) + " - " + formatTime(item.endTime);
        holder.tvTime.setText(timeString);
        holder.tvSubject.setText(item.subjectName);
        holder.tvRoomName.setText(item.roomName);

        // Update Status Colors cleanly
        if (item.attendanceStatus == null) {
            holder.tvStatus.setText("PENDING");
            holder.tvStatus.setTextColor(Color.GRAY);
        } else if (item.attendanceStatus == 1) {
            holder.tvStatus.setText("PRESENT");
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else if (item.attendanceStatus == 0) {
            holder.tvStatus.setText("BUNKED");
            holder.tvStatus.setTextColor(Color.parseColor("#F44336")); // Red
        } else if (item.attendanceStatus == 2) {
            holder.tvStatus.setText("CLASS CANCELLED");
            holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E")); // Grey
        }

        // Button Clicks
        holder.btnAttend.setOnClickListener(v -> clickListener.onActionClick(item, 1));
        holder.btnBunk.setOnClickListener(v -> clickListener.onActionClick(item, 0));
        holder.btnCancel.setOnClickListener(v -> clickListener.onActionClick(item, 2));
    }

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
        return lectureList.size();
    }

    public void setLectures(List<HomeLectureItem> newList) {
        this.lectureList = newList;
        notifyDataSetChanged();
    }
}