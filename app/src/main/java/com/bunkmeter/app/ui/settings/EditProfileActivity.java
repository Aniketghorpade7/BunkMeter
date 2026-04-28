package com.bunkmeter.app.ui.settings;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bunkmeter.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class EditProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private static final int CAMERA_IMAGE = 2;
    private static final int CAMERA_PERMISSION_CODE = 100;

    ImageView profileImage;
    EditText etName, etPRN, etDept, etSem;
    String imagePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        profileImage = findViewById(R.id.profileImage);
        etName = findViewById(R.id.etName);
        etPRN = findViewById(R.id.etPRN);
        etDept = findViewById(R.id.etDept);
        etSem = findViewById(R.id.etSem);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnChangeImage = findViewById(R.id.btnChangeImage);

        loadData();

        btnChangeImage.setOnClickListener(v -> showImagePickerDialog());

        btnSave.setOnClickListener(v -> saveData());
    }

    // ================= IMAGE PICKER =================

    private void showImagePickerDialog() {
        String[] options = {"Camera", "Gallery"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, CAMERA_IMAGE);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {

            try {
                if (requestCode == PICK_IMAGE) {
                    Uri imageUri = data.getData();
                    profileImage.setImageURI(imageUri);
                    imagePath = saveImageToInternalStorage(imageUri);

                } else if (requestCode == CAMERA_IMAGE) {
                    Bitmap photo = (Bitmap) data.getExtras().get("data");

                    File file = new File(getFilesDir(), "profile.jpg");
                    FileOutputStream fos = new FileOutputStream(file);

                    photo.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.close();

                    imagePath = file.getAbsolutePath();
                    profileImage.setImageURI(Uri.fromFile(file));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String saveImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File file = new File(getFilesDir(), "profile.jpg");

            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return file.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // ================= SAVE & LOAD =================

    private void saveData() {

        String name = etName.getText().toString().trim();
        String prn = etPRN.getText().toString().trim();
        String dept = etDept.getText().toString().trim();
        String sem = etSem.getText().toString().trim();

        // VALIDATION
        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        if (prn.isEmpty()) {
            etPRN.setError("PRN is required");
            etPRN.requestFocus();
            return;
        }

        if (dept.isEmpty()) {
            etDept.setError("Department is required");
            etDept.requestFocus();
            return;
        }

        if (sem.isEmpty()) {
            etSem.setError("Semester is required");
            etSem.requestFocus();
            return;
        }

        // Optional strict check
        if (!sem.matches("[1-8]")) {
            etSem.setError("Enter valid semester (1-8)");
            etSem.requestFocus();
            return;
        }

        // Save after validation
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("name", name);
        editor.putString("prn", prn);
        editor.putString("dept", dept);
        editor.putString("sem", sem);
        // Preserve existing image if none selected
        String existingImagePath = prefs.getString("image", "");
        if (imagePath.isEmpty()) {
            imagePath = existingImagePath;
        }
        editor.putString("image", imagePath);

        editor.apply();

        finish();
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("UserProfile", MODE_PRIVATE);

        etName.setText(prefs.getString("name", ""));
        etPRN.setText(prefs.getString("prn", ""));
        etDept.setText(prefs.getString("dept", ""));
        etSem.setText(prefs.getString("sem", ""));

        imagePath = prefs.getString("image", "");

        if (imagePath.isEmpty()) {
            Toast.makeText(this, "Please add your ID photo", Toast.LENGTH_SHORT).show();
        }

        if (!imagePath.isEmpty()) {
            profileImage.setImageURI(Uri.fromFile(new File(imagePath)));
        }
    }

    // ================= PERMISSION RESULT =================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}