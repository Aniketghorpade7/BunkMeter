package com.bunkmeter.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bunkmeter.app.R;
import com.bunkmeter.app.model.AttendanceStatus;
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

        // Status colors come from the single-source @color/status_* resources (the
        // same values the layouts use) instead of ad-hoc hardcoded hex.
        android.content.Context ctx = holder.itemView.getContext();
        if (item.attendanceStatus == null) {
            holder.tvStatus.setText("PENDING");
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_pending));
        } else {
            switch (AttendanceStatus.fromInt(item.attendanceStatus)) {
                case PRESENT:
                    holder.tvStatus.setText("PRESENT");
                    holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_present));
                    break;
                case CANCELLED:
                    holder.tvStatus.setText("CLASS CANCELLED");
                    holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_cancel));
                    break;
                case BUNK:
                default:
                    holder.tvStatus.setText("BUNKED");
                    holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_bunk));
                    break;
            }
        }

        // Button Clicks
        holder.btnAttend.setOnClickListener(v -> clickListener.onActionClick(item, AttendanceStatus.PRESENT.value));
        holder.btnBunk.setOnClickListener(v -> clickListener.onActionClick(item, AttendanceStatus.BUNK.value));
        holder.btnCancel.setOnClickListener(v -> clickListener.onActionClick(item, AttendanceStatus.CANCELLED.value));
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