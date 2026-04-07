package com.bunkmeter.app.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bunkmeter.app.R;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Timetable;
import com.bunkmeter.app.ui.settings.AddEditLectureDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private LectureAdapter adapter;
    private List<Timetable> timetableList = new ArrayList<>();

    private TextView tvEmptyHome;
    private FloatingActionButton fabAddLecture;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        tvEmptyHome = view.findViewById(R.id.tvEmptyHome);
        fabAddLecture = view.findViewById(R.id.fabAddLecture);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new LectureAdapter(timetableList, getTodayDateString());
        recyclerView.setAdapter(adapter);

        fabAddLecture.setOnClickListener(v -> {
            // Using the updated Dialog constructor
            AddEditLectureDialog dialog = new AddEditLectureDialog(
                    requireContext(),
                    null,
                    true,               // isTemporary = true for Home Screen
                    getTodayDateString(),
                    this::loadTodaysTimetable
            );
            dialog.show();
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTodaysTimetable();
    }

    private void loadTodaysTimetable() {
        String today = getTodayDateString();
        Calendar calendar = Calendar.getInstance();
        int mappedDay = getMappedDay(calendar.get(Calendar.DAY_OF_WEEK));

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext().getApplicationContext());

            List<Timetable> displayList = new ArrayList<>();
            if (mappedDay != -1) {
                displayList.addAll(db.timetableDao().getTimetableForDay(mappedDay));
            }

            List<Attendance> extraClasses = db.attendanceDao().getAttendanceForDate(today);

            for (Attendance a : extraClasses) {
                if (isExtraClass(a, displayList)) {
                    Timetable temp = new Timetable();
                    temp.setSubjectId(a.getSubjectId());
                    temp.setClassroomId(a.getClassroomId());
                    temp.setStartTime(a.getStartTime());
                    temp.setEndTime(a.getEndTime());
                    temp.setDayOfWeek(mappedDay);
                    displayList.add(temp);
                }
            }

            Collections.sort(displayList, (o1, o2) -> Integer.compare(o1.getStartTime(), o2.getStartTime()));

            requireActivity().runOnUiThread(() -> {
                if (displayList.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    tvEmptyHome.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyHome.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setTimetableList(displayList, today);
                }
            });
        });
    }

    private boolean isExtraClass(Attendance a, List<Timetable> schedule) {
        for (Timetable t : schedule) {
            if (t.getSubjectId() == a.getSubjectId() && t.getStartTime() == a.getStartTime()) {
                return false;
            }
        }
        return true;
    }

    private String getTodayDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(Calendar.getInstance().getTime());
    }

    private int getMappedDay(int currentDayOfWeek) {
        if (currentDayOfWeek == Calendar.MONDAY) return 0;
        if (currentDayOfWeek == Calendar.TUESDAY) return 1;
        if (currentDayOfWeek == Calendar.WEDNESDAY) return 2;
        if (currentDayOfWeek == Calendar.THURSDAY) return 3;
        if (currentDayOfWeek == Calendar.FRIDAY) return 4;
        return -1;
    }
}