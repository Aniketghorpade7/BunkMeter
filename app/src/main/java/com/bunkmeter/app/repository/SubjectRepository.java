package com.bunkmeter.app.repository;

import android.app.Application;

import com.bunkmeter.app.database.SubjectDao;
import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Subject;

import java.util.List;

public class SubjectRepository {

    private final SubjectDao subjectDao;

    public SubjectRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        subjectDao = db.subjectDao();
    }

    public void insert(Subject subject) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                subjectDao.insertSubject(subject));
    }

    public void update(Subject subject) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                subjectDao.updateSubject(subject));
    }

    public void delete(Subject subject) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                subjectDao.deleteSubject(subject));
    }

    public List<Subject> getAllSubjects() {
        return subjectDao.getAllSubjects();
    }

    public int getSubjectCount() {
        return subjectDao.getSubjectCount();
    }
}