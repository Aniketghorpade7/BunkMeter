package com.bunkmeter.app.ui.settings.classroom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Classroom;
import java.util.ArrayList;
import java.util.List;

public class ClassroomAdapter extends RecyclerView.Adapter<ClassroomAdapter.ViewHolder> {
    private List<Classroom> classrooms = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener { void onItemClick(Classroom classroom); }
    public interface OnItemLongClickListener { void onItemLongClick(Classroom classroom); }

    public ClassroomAdapter(OnItemClickListener cl, OnItemLongClickListener lcl) {
        this.clickListener = cl;
        this.longClickListener = lcl;
    }

    public void setClassrooms(List<Classroom> classrooms) {
        this.classrooms = classrooms;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_classroom, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Classroom c = classrooms.get(position);
        holder.tvName.setText(c.getName());
        holder.itemView.setOnClickListener(v -> clickListener.onItemClick(c));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onItemLongClick(c);
            return true;
        });
    }

    @Override
    public int getItemCount() { return classrooms.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ViewHolder(View view) {
            super(view);
            tvName = view.findViewById(R.id.tvItemClassName);
        }
    }
}