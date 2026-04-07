package com.bunkmeter.app.ui.main;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bunkmeter.app.R;
import com.bunkmeter.app.ui.home.HomeFragment;
import com.bunkmeter.app.ui.settings.SettingsFragment;
import com.bunkmeter.app.ui.subject.LectureListFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_main); // or your layout

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });


        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        // Default screen
        loadFragment(new HomeFragment());
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {

            if (item.getItemId() == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            }
            else if (item.getItemId() == R.id.nav_subjects) {
                loadFragment(new LectureListFragment()); // create later
                return true;
            }
            else if (item.getItemId() == R.id.nav_settings) {
                loadFragment(new SettingsFragment());
                return true;
            }

            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}