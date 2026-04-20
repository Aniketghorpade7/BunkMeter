package com.bunkmeter.app.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.bunkmeter.app.R;
import com.bunkmeter.app.repository.ResetRepository;
import com.bunkmeter.app.ui.main.MainActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import java.io.File;

public class SettingsFragment extends Fragment {

    private ImageView idCardImage;
    private TextView tvStudentName, tvPRN, tvDept, tvSem;
    private AlertDialog progressDialog;

    // Modern launcher to handle returning from ExportActivity
    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // If the export was successful and meant for a reset, perform the reset!
                if (result.getData() != null && result.getData().getBooleanExtra("export_success", false)) {
                    performReset();
                }
            }
    );

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

        // --- SECRET DEVELOPER BUTTON: Long-press to inject data ---
        idCardImage.setOnLongClickListener(v -> {
            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Developer Tools")
                    .setMessage("Inject 3 months of fake data into the database for testing?")
                    .setPositiveButton("Inject Data", (dialog, which) -> {
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
            showResetWarningBottomSheet();
        });

        return view;
    }

    // ================= RESET APP LOGIC =================
    private void showResetWarningBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_reset_warning, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        bottomSheetView.findViewById(R.id.btnCancel).setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetView.findViewById(R.id.btnContinue).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showExportPromptBottomSheet();
        });

        bottomSheetDialog.show();
    }

    private void showExportPromptBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View bottomSheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_reset_export, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        bottomSheetView.findViewById(R.id.btnSkip).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            performReset(); // Proceed directly to reset without exporting
        });

        bottomSheetView.findViewById(R.id.btnExportAndContinue).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(getActivity(), ExportActivity.class);
            intent.putExtra("fromReset", true); // Pass flag to ExportActivity
            exportLauncher.launch(intent); // Launch with our modern listener
        });

        bottomSheetDialog.show();
    }

    private void performReset() {
        showProgressDialog();

        ResetRepository repository = new ResetRepository(requireContext());
        repository.resetAppData(new ResetRepository.ResetCallback() {
            @Override
            public void onSuccess() {
                hideProgressDialog();
                Toast.makeText(getContext(), "App reset successful", Toast.LENGTH_SHORT).show();

                // Navigate back to a clean MainActivity
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }

            @Override
            public void onError(Exception e) {
                hideProgressDialog();
                Toast.makeText(getContext(), "Error resetting app: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_progress_reset, null);
            builder.setView(view);
            builder.setCancelable(false); // MUST NOT BE CANCELLED during db operations
            progressDialog = builder.create();
            if (progressDialog.getWindow() != null) {
                progressDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
        progressDialog.show();
    }

    private void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    // ================= LOAD PROFILE =================
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
            idCardImage.setImageResource(R.drawable.ic_id_card_placeholder);
        }
    }

    private BroadcastReceiver profileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Reload profile data (including image) when broadcast is received
            loadProfileData();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // Register for profile update broadcasts
        IntentFilter filter = new IntentFilter("com.bunkmeter.app.ACTION_PROFILE_UPDATED");
        requireContext().registerReceiver(profileUpdateReceiver, filter);
        loadProfileData(); // Ensure data is fresh when fragment becomes visible
    }

    @Override
    public void onPause() {
        super.onPause();
        // Unregister to avoid leaks
        try {
            requireContext().unregisterReceiver(profileUpdateReceiver);
        } catch (IllegalArgumentException ignored) {}
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