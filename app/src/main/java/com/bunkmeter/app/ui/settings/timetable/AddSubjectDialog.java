package com.bunkmeter.app.ui.settings.timetable;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.bunkmeter.app.R;
import com.bunkmeter.app.model.Subject;
import com.bunkmeter.app.repository.SubjectRepository;

import java.util.concurrent.Executors;

import yuku.ambilwarna.AmbilWarnaDialog;

public class AddSubjectDialog extends Dialog {

    private Context context;
    private SubjectRepository repo;
    private int selectedColor = Color.parseColor("#2196F3");

    // Variables for editing
    private Subject existingSubject;
    private Runnable onSaved;

    // Updated Constructor expects 3 parameters now!
    public AddSubjectDialog(Context context, Subject existingSubject, Runnable onSaved) {
        super(context);
        this.context = context;
        this.existingSubject = existingSubject;
        this.onSaved = onSaved;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_add_subject);

        repo = new SubjectRepository((Application) context.getApplicationContext());

        EditText etName = findViewById(R.id.etSubjectName);
        Button btnSave = findViewById(R.id.btnSaveSubject);
        com.google.android.material.card.MaterialCardView colorCircle = findViewById(R.id.colorCircle);

        // IF EDITING: Auto-fill the existing details
        if (existingSubject != null) {
            etName.setText(existingSubject.getName());
            btnSave.setText("Update"); // Change button text
            try {
                selectedColor = Color.parseColor(existingSubject.getColor());
            } catch (Exception ignored) {}
        }

        colorCircle.setCardBackgroundColor(selectedColor);
        colorCircle.setOnClickListener(v -> openColorPicker(colorCircle));

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Enter subject name");
                return;
            }

            String hexColor = String.format("#%06X", (0xFFFFFF & selectedColor));

            // Save to database on background thread
            Executors.newSingleThreadExecutor().execute(() -> {
                if (existingSubject != null) {
                    // Update existing subject
                    existingSubject.setName(name);
                    existingSubject.setColor(hexColor);
                    repo.update(existingSubject);
                } else {
                    // Insert new subject
                    repo.insert(new Subject(name, hexColor));
                }

                ((Activity) context).runOnUiThread(() -> {
                    dismiss();
                    if (onSaved != null) onSaved.run(); // Refresh the list!
                });
            });
        });
    }

    private void openColorPicker(com.google.android.material.card.MaterialCardView colorCircle) {
        AmbilWarnaDialog colorPicker = new AmbilWarnaDialog(context, selectedColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) { }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                selectedColor = color;
                colorCircle.setCardBackgroundColor(selectedColor);
            }
        });
        colorPicker.show();
    }
}