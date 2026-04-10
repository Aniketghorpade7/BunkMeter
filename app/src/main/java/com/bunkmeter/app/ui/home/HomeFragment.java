package com.bunkmeter.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bunkmeter.app.R;
import com.bunkmeter.app.ui.settings.AddEditLectureDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private LectureAdapter adapter;
    private TextView tvEmptyHome;
    private RecyclerView recyclerView;

    public HomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        tvEmptyHome = view.findViewById(R.id.tvEmptyHome);
        FloatingActionButton fabAddLecture = view.findViewById(R.id.fabAddLecture);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Initialize Adapter and pass the click listener back to the ViewModel
        adapter = new LectureAdapter((item, statusValue) -> {
            // item.classroomId is now properly populated by Room (null if no classroom assigned)
            viewModel.markAttendance(item.subjectId, item.startTime, item.classroomId, statusValue);
        });
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // OBSERVE LiveData - UI updates automatically!
        viewModel.getTodaysLectures().observe(getViewLifecycleOwner(), lectures -> {
            if (lectures == null || lectures.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                tvEmptyHome.setVisibility(View.VISIBLE);
            } else {
                tvEmptyHome.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setLectures(lectures);
            }
        });

        // FAB Click
        fabAddLecture.setOnClickListener(v -> {
            AddEditLectureDialog dialog = new AddEditLectureDialog(
                    requireContext(),
                    null,
                    true,
                    viewModel.getTodayDateString(),
                    null // Since LiveData auto-updates, we no longer need to pass a manual refresh callback!
            );
            dialog.show();
        });

        return view;
    }
}