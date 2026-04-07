package com.bunkmeter.app.ui.subject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Subject;

import java.util.ArrayList;
import java.util.List;

public class LectureListFragment extends Fragment {

    private RecyclerView recyclerView;
    private SubjectAdapter adapter;
    private TextView tvEmptySubjects; // 1. Added the TextView variable

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Note: Make sure your XML file is actually named fragment_subject_list
        View view = inflater.inflate(R.layout.fragment_subject_list, container, false);

        recyclerView = view.findViewById(R.id.rvSubjects);

        // 2. Link the TextView (Ensure this ID exists in fragment_subject_list.xml)
        tvEmptySubjects = view.findViewById(R.id.tvEmptySubjects);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SubjectAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        return view;
    }

    private void loadSubjects() {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            List<Subject> data = db.subjectDao().getAllSubjects();

            requireActivity().runOnUiThread(() -> {
                // 3. The magic toggle logic!
                if (data == null || data.isEmpty()) {
                    // Hide the list, show the funny message
                    recyclerView.setVisibility(View.GONE);
                    if (tvEmptySubjects != null) tvEmptySubjects.setVisibility(View.VISIBLE);
                } else {
                    // Show the list, hide the message
                    if (tvEmptySubjects != null) tvEmptySubjects.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.updateSubjects(data);
                }
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 4. This guarantees the screen refreshes EVERY time you tap the Subjects tab!
        loadSubjects();
    }
}