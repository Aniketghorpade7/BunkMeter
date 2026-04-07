package com.bunkmeter.app.ui.settings.classroom;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.repository.ClassroomRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AddEditClassroomBottomSheet extends BottomSheetDialogFragment {

    private ClassroomRepository repository;
    private FusedLocationProviderClient fusedLocationClient;

    private EditText etName;
    private TextView tvLocationStatus, tvRadiusValue, tvTestResult;
    private SeekBar sbRadius;

    private Integer classroomId = null;
    private Double savedLat = null;
    private Double savedLon = null;
    private float currentRadius = 10f;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) fetchCurrentLocation(false);
                else Toast.makeText(getContext(), "Location permission required", Toast.LENGTH_SHORT).show();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_edit_classroom, container, false);

        repository = new ClassroomRepository(requireActivity().getApplication());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        etName = view.findViewById(R.id.etClassroomName);
        tvLocationStatus = view.findViewById(R.id.tvLocationStatus);
        tvRadiusValue = view.findViewById(R.id.tvRadiusValue);
        tvTestResult = view.findViewById(R.id.tvTestResult);
        sbRadius = view.findViewById(R.id.sbRadius);
        Button btnSetLocation = view.findViewById(R.id.btnSetLocation);
        Button btnTest = view.findViewById(R.id.btnTestClassroom);
        Button btnSave = view.findViewById(R.id.btnSave);
        Button btnDelete = view.findViewById(R.id.btnDeleteClassroom);

        sbRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentRadius = progress + 10f;
                tvRadiusValue.setText((int)currentRadius + " meters");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        if (getArguments() != null) {
            classroomId = getArguments().getInt("CLASSROOM_ID", -1);
            if (classroomId != -1) {
                loadClassroomData(classroomId);
            } else {
                classroomId = null;
            }
        }

        btnSetLocation.setOnClickListener(v -> checkPermissionAndFetch(false));
        btnTest.setOnClickListener(v -> testClassroom());
        btnSave.setOnClickListener(v -> saveClassroom());

        if (classroomId != null) {
            btnDelete.setVisibility(View.VISIBLE); // Show button if editing
            btnDelete.setOnClickListener(v -> {
                repository.softDelete(classroomId, () -> {
                    getParentFragmentManager().setFragmentResult("REFRESH_CLASSROOMS", new Bundle());
                    dismiss();
                });
            });
        }

        return view;
    }

    private void loadClassroomData(int id) {
        repository.getClassroomById(id, classroom -> {
            if (classroom != null) {
                etName.setText(classroom.getName());
                savedLat = classroom.getLatitude();
                savedLon = classroom.getLongitude();
                currentRadius = classroom.getRadius();
                sbRadius.setProgress((int)currentRadius - 10);
                updateLocationUI();
            }
        });
    }

    private void checkPermissionAndFetch(boolean isTest) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation(isTest);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void fetchCurrentLocation(boolean isTest) {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    if (isTest) {
                        calculateDistance(location);
                    } else {
                        savedLat = location.getLatitude();
                        savedLon = location.getLongitude();
                        updateLocationUI();
                        Toast.makeText(getContext(), "Location set successfully", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getContext(), "Ensure GPS is enabled", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void updateLocationUI() {
        if (savedLat != null && savedLon != null) {
            tvLocationStatus.setText("Location Set ✅");
            tvLocationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
        }
    }

    private void testClassroom() {
        if (savedLat == null || savedLon == null) {
            Toast.makeText(getContext(), "Set location first", Toast.LENGTH_SHORT).show();
            return;
        }
        checkPermissionAndFetch(true);
    }

    private void calculateDistance(Location currentLocation) {
        float[] results = new float[1];
        Location.distanceBetween(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                savedLat, savedLon,
                results
        );
        float distance = results[0];

        if (distance <= currentRadius) {
            tvTestResult.setText(String.format("Distance: %.1f m\nStatus: Inside ✅", distance));
            tvTestResult.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
        } else {
            tvTestResult.setText(String.format("Distance: %.1f m\nStatus: Outside ❌", distance));
            tvTestResult.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
        }
    }

    private void saveClassroom() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError("Name required");
            return;
        }
        if (savedLat == null || savedLon == null) {
            Toast.makeText(getContext(), "Please set location", Toast.LENGTH_SHORT).show();
            return;
        }

        Classroom classroom = new Classroom();
        classroom.setName(name);
        classroom.setLatitude(savedLat);
        classroom.setLongitude(savedLon);
        classroom.setRadius(currentRadius);
        classroom.setActive(true);

        // Notify parent fragment to refresh list upon completion
        ClassroomRepository.OnOperationCompleteListener completeListener = () -> {
            getParentFragmentManager().setFragmentResult("REFRESH_CLASSROOMS", new Bundle());
            dismiss(); // Close the bottom sheet
        };

        if (classroomId == null) {
            repository.insert(classroom, completeListener);
        } else {
            classroom.setClassroomId(classroomId);
            repository.update(classroom, completeListener);
        }
    }
}