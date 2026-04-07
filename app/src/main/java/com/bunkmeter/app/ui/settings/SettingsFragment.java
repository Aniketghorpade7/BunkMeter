package com.bunkmeter.app.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.bunkmeter.app.R;
import com.bunkmeter.app.ui.settings.EditProfileActivity;

import java.io.File;

public class SettingsFragment extends Fragment {

    private ImageView idCardImage;
    private TextView tvStudentName, tvPRN, tvDept, tvSem;

    public SettingsFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        LinearLayout cardProfile = view.findViewById(R.id.cardProfile);
        LinearLayout cardTimetable = view.findViewById(R.id.cardTimetable);
        LinearLayout cardClassroom = view.findViewById(R.id.cardClassroom);
        LinearLayout cardExport = view.findViewById(R.id.cardExport);
        LinearLayout cardReset = view.findViewById(R.id.cardReset);

        idCardImage = view.findViewById(R.id.idCardImage);
        tvStudentName = view.findViewById(R.id.tvStudentName);
        tvPRN = view.findViewById(R.id.tvPRN);
        tvDept = view.findViewById(R.id.tvDept);
        tvSem = view.findViewById(R.id.tvSem);

        idCardImage.setOnClickListener(v -> showImagePreview());

        //Dummy data called
        // --- SECRET DEVELOPER BUTTON: Long-press to inject data ---
        idCardImage.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Developer Tools")
                    .setMessage("Inject 30 days of fake data into the database for testing?")
                    .setPositiveButton("Inject Data", (dialog, which) -> {
                        // THIS is the line that connects to and runs your new file!
                        com.bunkmeter.app.utils.MockDataGenerator.injectDummyData(getContext(), getActivity());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        cardProfile.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), EditProfileActivity.class)));

        cardTimetable.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), TimetableActivity.class)));

        cardClassroom.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ClassroomActivity.class)));

        cardExport.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), ExportActivity.class)));


        cardReset.setOnClickListener(v -> {
            // TODO BottomSheet
        });

        return view;
    }

    private void loadProfileData() {

        SharedPreferences prefs = getContext().getSharedPreferences("UserProfile", Context.MODE_PRIVATE);

        String name = prefs.getString("name", "Your Name");
        String prn = prefs.getString("prn", "PRN");
        String dept = prefs.getString("dept", "Department");
        String sem = prefs.getString("sem", "Semester");
        String imagePath = prefs.getString("image", "");

        // Set data
        tvStudentName.setText(name);
        tvPRN.setText(prn);
        tvDept.setText(dept);
        tvSem.setText(sem);

        // Image
        if (!imagePath.isEmpty()) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                idCardImage.setImageURI(Uri.fromFile(imgFile));
            }
        } else {
            idCardImage.setImageResource(R.drawable.ic_profile_placeholder);
        }
    }

    // ================= LOAD PROFILE =================
    @Override
    public void onResume() {
        super.onResume();
        loadProfileData();
    }

    // ================= IMAGE PREVIEW =================
    private void showImagePreview() {

        android.app.Dialog dialog = new android.app.Dialog(getActivity());
        dialog.setContentView(R.layout.dialog_image_preview);

        ImageView fullImage = dialog.findViewById(R.id.fullImage);
        ImageView original = getView().findViewById(R.id.idCardImage);

        fullImage.setImageDrawable(original.getDrawable());

        dialog.getWindow().getAttributes().windowAnimations =
                android.R.style.Animation_Dialog;

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        dialog.show();

        dialog.findViewById(android.R.id.content)
                .setOnClickListener(v -> dialog.dismiss());
    }
}