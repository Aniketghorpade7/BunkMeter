package com.bunkmeter.app.ui.subject;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.repository.AttendanceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SubjectViewModel extends AndroidViewModel {

    private final AttendanceRepository repository;
    private LiveData<List<Attendance>> attendanceHistory;

    public SubjectViewModel(@NonNull Application application) {
        super(application);
        repository = new AttendanceRepository(application);
    }

    public void init(int subjectId) {
        if (attendanceHistory == null) {
            // Assumes you added getLiveAttendanceBySubject() to your DAO/Repository
            // Query: SELECT * FROM Attendance WHERE subjectId = :subjectId
            attendanceHistory = repository.getLiveAttendanceBySubject(subjectId);
        }
    }

    public LiveData<List<Attendance>> getAttendanceHistory() {
        return attendanceHistory;
    }

    // A helper class to pass both the percentage and the grid data back to the UI cleanly
    public static class SubjectStats {
        public String percentageText;
        public List<Integer> gridData;

        public SubjectStats(String percentageText, List<Integer> gridData) {
            this.percentageText = percentageText;
            this.gridData = gridData;
        }
    }

    // This perfectly preserves your legacy "Day-Based" grouping logic
    public SubjectStats processAttendanceStats(List<Attendance> list) {
        if (list == null || list.isEmpty()) {
            return new SubjectStats("Attendance: 0%", new ArrayList<>());
        }

        Map<String, Integer> dailyMap = new LinkedHashMap<>();

        for (Attendance a : list) {
            String date = a.getDate();
            if (!dailyMap.containsKey(date)) {
                dailyMap.put(date, 0);
            }
            if (a.getStatus() == Attendance.PRESENT) {
                dailyMap.put(date, dailyMap.get(date) + 1);
            }
        }

        List<Integer> gridData = new ArrayList<>();
        int presentDays = 0;

        for (String date : dailyMap.keySet()) {
            int presentCount = dailyMap.get(date);
            if (presentCount > 0) {
                gridData.add(1); // present
                presentDays++;
            } else {
                gridData.add(0); // absent
            }
        }

        int totalDays = dailyMap.size();
        float percentage = totalDays == 0 ? 0 : (presentDays * 100f) / totalDays;

        return new SubjectStats("Attendance: " + (int) percentage + "%", gridData);
    }
}