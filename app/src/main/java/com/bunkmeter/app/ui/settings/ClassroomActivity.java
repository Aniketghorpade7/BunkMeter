package com.bunkmeter.app.ui.settings;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.bunkmeter.app.R;
import com.bunkmeter.app.ui.settings.classroom.ClassroomListFragment;

public class ClassroomActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classroom);
        setTitle("Classrooms");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ClassroomListFragment())
                    .commit();
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}