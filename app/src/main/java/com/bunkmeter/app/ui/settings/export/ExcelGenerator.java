package com.bunkmeter.app.ui.settings.export;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelGenerator {

    public void generateReport(File outputFile, List<Subject> subjects, List<Attendance> attendances, boolean includeCharts, boolean includeDaily) throws Exception {

        Workbook workbook = new XSSFWorkbook();

        // --- MAP SUBJECT IDs TO NAMES FOR CLEANER EXCEL DATA ---
        Map<Integer, String> subjectMap = new HashMap<>();
        for (Subject s : subjects) {
            subjectMap.put(s.getSubjectId(), s.getName());
        }

        // --- SHEET 1: ATTENDANCE LOG ---
        Sheet logSheet = workbook.createSheet("Attendance_Log");
        Row headerRow = logSheet.createRow(0);
        headerRow.createCell(0).setCellValue("Date");
        headerRow.createCell(1).setCellValue("Subject");
        headerRow.createCell(2).setCellValue("Status");

        int rowIdx = 1;
        for (Attendance a : attendances) {
            Row row = logSheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(a.getDate()); // Assumes string YYYY-MM-DD
            row.createCell(1).setCellValue(subjectMap.getOrDefault(a.getSubjectId(), "Unknown"));
            row.createCell(2).setCellValue(a.getStatus() == 1 ? "Present" : "Absent"); // e.g. "Present" or "Absent"
        }

        // --- SHEET 2: SUBJECT SUMMARY ---
        Sheet summarySheet = workbook.createSheet("Subject_Summary");
        Row sumHeader = summarySheet.createRow(0);
        sumHeader.createCell(0).setCellValue("Subject");
        sumHeader.createCell(1).setCellValue("Total Classes");
        sumHeader.createCell(2).setCellValue("Attended");
        sumHeader.createCell(3).setCellValue("Attendance %");

        int sumRowIdx = 1;
        for (Subject s : subjects) {
            int attended = 0;
            int total = 0;

            for (Attendance a : attendances) {
                if (a.getSubjectId() == s.getSubjectId()) {
                    total++;
                    if (a.getStatus() == 1) {
                        attended++;
                    }
                }
            }

            double percent = total == 0 ? 0 : ((double) attended / total) * 100;

            Row row = summarySheet.createRow(sumRowIdx++);
            row.createCell(0).setCellValue(s.getName());
            row.createCell(1).setCellValue(total);
            row.createCell(2).setCellValue(attended);
            row.createCell(3).setCellValue(String.format("%.2f%%", percent));
        }

        // --- SHEET 3: DAILY SUMMARY (Optional) ---
        if (includeDaily) {
            Sheet dailySheet = workbook.createSheet("Daily_Summary");
            Row dailyHeader = dailySheet.createRow(0);
            dailyHeader.createCell(0).setCellValue("Notice");
            Row dRow = dailySheet.createRow(1);
            dRow.createCell(0).setCellValue("Daily data generation logic can be expanded here.");
        }

        // --- SHEET 4: CHARTS (Optional) ---
        if (includeCharts) {
            Sheet chartSheet = workbook.createSheet("Charts");
            Row chartHeader = chartSheet.createRow(0);
            chartHeader.createCell(0).setCellValue("Note regarding Charts in Android POI:");

            Row cRow = chartSheet.createRow(1);
            cRow.createCell(0).setCellValue("Native chart drawing (XSSFDrawing) requires massive XML configuration which is heavily memory-intensive on Android devices. It is highly recommended to generate charts using an external library (like MPAndroidChart) inside the app rather than inside the Excel file to prevent OutOfMemory crashes.");
        }

        // --- WRITE TO FILE ---
        try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
            workbook.write(fileOut);
        }
        workbook.close();
    }

    private void createAttendanceLog(Sheet sheet, List<Attendance> allAttendance, AppDatabase db) {
        // Set Header Row
        String[] headers = {"Subject", "Classroom", "Date", "Time", "Status"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Fill Data
        int rowNum = 1;
        for (Attendance a : allAttendance) {
            Row row = sheet.createRow(rowNum++);

            // Fetch Subject
            Subject s = db.subjectDao().getSubjectById(a.getSubjectId());
            row.createCell(0).setCellValue(s != null ? s.getName() : "Unknown");

            // Fetch Room (Handles Extra Lectures automatically)
            String roomName = "N/A";
            if (a.getClassroomId() != 0) {
                Classroom r = db.classroomDao().getClassroomById(a.getClassroomId());
                if (r != null) roomName = r.getName();
            }
            row.createCell(1).setCellValue(roomName);

            row.createCell(2).setCellValue(a.getDate());
            row.createCell(3).setCellValue(formatTime(a.getStartTime()));
            row.createCell(4).setCellValue(a.getStatus() == 1 ? "Present" : "Bunked");
        }
    }

    private String formatTime(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private void createChartSheet(Workbook workbook, List<Attendance> allAttendance) {
        Sheet sheet = workbook.createSheet("Charts");

        int presentCount = 0;
        int absentCount = 0;

        for (Attendance a : allAttendance) {
            if (a.getStatus() == 1) presentCount++;
            else if (a.getStatus() == 0) absentCount++;
        }

        // Create a simple table that Excel can immediately turn into a chart
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Status");
        header.createCell(1).setCellValue("Count");

        Row row1 = sheet.createRow(1);
        row1.createCell(0).setCellValue("Present");
        row1.createCell(1).setCellValue(presentCount);

        Row row2 = sheet.createRow(2);
        row2.createCell(0).setCellValue("Bunked");
        row2.createCell(1).setCellValue(absentCount);
    }
}