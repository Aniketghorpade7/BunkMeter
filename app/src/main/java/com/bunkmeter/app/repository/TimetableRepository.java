package com.bunkmeter.app.repository;

import android.app.Application;

import com.bunkmeter.app.database.TimetableDao;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Timetable;

import java.util.List;

public class TimetableRepository {

    private final TimetableDao timetableDao;

    public TimetableRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        timetableDao = db.timetableDao();
    }

    public void insert(Timetable timetable) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                timetableDao.insertTimetable(timetable));
    }

    public void update(Timetable timetable) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                timetableDao.updateTimetable(timetable));
    }

    public void delete(Timetable timetable) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                timetableDao.deleteTimetable(timetable));
    }

    public List<Timetable> getFullTimetable() {
        return timetableDao.getFullTimetable();
    }

    public List<Timetable> getTimetableForDay(int day) {
        return timetableDao.getTimetableForDay(day);
    }

    // Conflict handling
    public List<Timetable> getConflicts(int day, int start, int end) {
        return timetableDao.getConflictingSlots(day, start, end);
    }

    public void replaceConflicts(Timetable timetable) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            timetableDao.deleteConflicts(
                    timetable.getDayOfWeek(),
                    timetable.getStartTime(),
                    timetable.getEndTime()
            );
            timetableDao.insertTimetable(timetable);
        });
    }
}