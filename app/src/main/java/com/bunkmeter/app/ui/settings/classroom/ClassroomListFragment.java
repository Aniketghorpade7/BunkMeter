package com.bunkmeter.app.ui.settings.classroom;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.repository.ClassroomRepository;
import com.bunkmeter.app.ui.settings.ClassroomActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ClassroomListFragment extends Fragment {
    private ClassroomRepository repository;
    private ClassroomAdapter adapter;
    private TextView tvEmptyState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_classroom_list, container, false);

        repository = new ClassroomRepository(requireActivity().getApplication());
        RecyclerView rv = view.findViewById(R.id.rvClassrooms);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        FloatingActionButton fab = view.findViewById(R.id.fabAddClassroom);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        // Update adapter click listeners to launch Bottom Sheet
        adapter = new ClassroomAdapter(
                classroom -> showBottomSheet(classroom.getClassroomId()),
                classroom -> showDeleteConfirmation(classroom)
        );
        rv.setAdapter(adapter);

        // Update FAB to launch Bottom Sheet
        fab.setOnClickListener(v -> showBottomSheet(null));

        // Listen for the refresh signal from the Bottom Sheet
        getParentFragmentManager().setFragmentResultListener("REFRESH_CLASSROOMS", getViewLifecycleOwner(), (requestKey, result) -> {
            loadClassrooms();
        });

        loadClassrooms();
        return view;
    }

    // New helper method to show the Bottom Sheet cleanly
    private void showBottomSheet(Integer classroomId) {
        AddEditClassroomBottomSheet bottomSheet = new AddEditClassroomBottomSheet();
        if (classroomId != null) {
            Bundle args = new Bundle();
            args.putInt("CLASSROOM_ID", classroomId);
            bottomSheet.setArguments(args);
        }
        bottomSheet.show(getParentFragmentManager(), "AddEditClassroomBottomSheet");
    }

    private void loadClassrooms() {
        repository.getActiveClassrooms(classrooms -> {
            adapter.setClassrooms(classrooms);
            tvEmptyState.setVisibility(classrooms.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void showDeleteConfirmation(Classroom classroom) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Classroom")
                .setMessage("This classroom is used in timetable. Deleting it may affect attendance tracking. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.softDelete(classroom.getClassroomId(), () -> {
                        Toast.makeText(getContext(), "Classroom deleted", Toast.LENGTH_SHORT).show();
                        loadClassrooms();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}