package com.bunkmeter.app.ui.subject;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;

import java.util.List;

public class HeatmapAdapter extends RecyclerView.Adapter<HeatmapAdapter.ViewHolder> {

    private List<Integer> list;

    public HeatmapAdapter(List<Integer> list) {
        this.list = list;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_heatmap, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        int val = list.get(position);

        if (val == 1) {
            holder.box.setBackgroundColor(Color.parseColor("#4CAF50")); // present
        } else if (val == -1) {
            holder.box.setBackgroundColor(Color.parseColor("#F44336")); // absent
        } else {
            holder.box.setBackgroundColor(Color.parseColor("#E0E0E0")); // no lecture
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View box;

        ViewHolder(View itemView) {
            super(itemView);
            box = itemView.findViewById(R.id.box);
        }
    }
}