package com.bunkmeter.app.ui.settings.timetable;

import android.app.Application;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.repository.SubjectRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ManageSubjectsBottomSheet extends BottomSheetDialogFragment {

    private SubjectRepository subjectRepo;
    private RecyclerView rvSubjects;
    private SubjectListAdapter adapter;
    private List<Subject> subjectList = new ArrayList<>();
    private Runnable onDataChanged; // Refreshes the calendar behind it

    public ManageSubjectsBottomSheet(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_manage_subjects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        subjectRepo = new SubjectRepository((Application) requireContext().getApplicationContext());
        rvSubjects = view.findViewById(R.id.rvSubjects);
        rvSubjects.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new SubjectListAdapter();
        rvSubjects.setAdapter(adapter);

        view.findViewById(R.id.btnAddNewSubject).setOnClickListener(v -> {
            // Open your existing AddSubjectDialog (passing null means "New Subject")
            new AddSubjectDialog(requireContext(), null, this::loadSubjects).show();
        });

        loadSubjects();
    }

    private void loadSubjects() {
        Executors.newSingleThreadExecutor().execute(() -> {
            subjectList = subjectRepo.getAllSubjects();
            requireActivity().runOnUiThread(() -> {
                adapter.notifyDataSetChanged();
                onDataChanged.run(); // Update the main timetable calendar too!
            });
        });
    }

    // --- OPTION 4: THE SCARY DELETE ---
    private void showScaryDeleteDialog(Subject subject) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("⚠️ DANGER: Delete Subject?");
        builder.setMessage("This will permanently delete '" + subject.getName() +
                "' AND wipe out all Timetable slots attached to it.\n\nType DELETE to confirm.");

        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Type DELETE");
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            if (input.getText().toString().trim().equals("DELETE")) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    subjectRepo.delete(subject); // Cascade delete triggers!
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Subject Nuked ☢️", Toast.LENGTH_SHORT).show();
                        loadSubjects();
                    });
                });
            } else {
                Toast.makeText(requireContext(), "Cancelled. You didn't type DELETE.", Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        android.app.AlertDialog alert = builder.create();
        alert.show();
        alert.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED);
    }

    // --- INNER ADAPTER ---
    private class SubjectListAdapter extends RecyclerView.Adapter<SubjectListAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_subject, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder h, int position) {
            Subject s = subjectList.get(position);
            h.txtName.setText(s.getName());
            try { h.colorDot.setCardBackgroundColor(Color.parseColor(s.getColor())); } catch (Exception ignored){}

            // Open edit dialog
            h.btnEdit.setOnClickListener(v -> new AddSubjectDialog(requireContext(), s, ManageSubjectsBottomSheet.this::loadSubjects).show());

            // Trigger scary delete
            h.btnDelete.setOnClickListener(v -> showScaryDeleteDialog(s));
        }

        @Override
        public int getItemCount() { return subjectList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtName;
            com.google.android.material.card.MaterialCardView colorDot;
            ImageView btnEdit, btnDelete;
            ViewHolder(View v) {
                super(v);
                txtName = v.findViewById(R.id.txtSubjectName);
                colorDot = v.findViewById(R.id.subjectColorDot);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}