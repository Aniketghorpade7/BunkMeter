package com.bunkmeter.app.ui.settings.export;

import com.bunkmeter.app.database.AppDatabase;
import com.bunkmeter.app.model.Attendance;
import com.bunkmeter.app.model.Classroom;
import com.bunkmeter.app.model.Subject;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import java.io.ByteArrayOutputStream;

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

    public void generateReport(Context context, File outputFile, List<Subject> subjects, List<Attendance> attendances, boolean includeCharts, boolean includeDaily) throws Exception {

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
            
            int present = 0, absent = 0;
            for (Attendance a : attendances) {
                if (a.getStatus() == 1) present++;
                else if (a.getStatus() == 0) absent++;
            }

            byte[] chartBytes = generateChartImage(context, present, absent);

            int pictureIdx = workbook.addPicture(chartBytes, Workbook.PICTURE_TYPE_PNG);
            org.apache.poi.ss.usermodel.CreationHelper helper = workbook.getCreationHelper();
            org.apache.poi.ss.usermodel.Drawing<?> drawing = chartSheet.createDrawingPatriarch();
            org.apache.poi.ss.usermodel.ClientAnchor anchor = helper.createClientAnchor();

            anchor.setCol1(1);
            anchor.setRow1(1);
            anchor.setCol2(8); // span 7 cols
            anchor.setRow2(20); // span 19 rows

            org.apache.poi.ss.usermodel.Picture pict = drawing.createPicture(anchor, pictureIdx);
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

    private byte[] generateChartImage(Context context, int present, int absent) throws Exception {
        final byte[][] result = new byte[1][1];
        final Exception[] error = new Exception[1];
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                PieChart chart = new PieChart(context);
                chart.setLayoutParams(new android.view.ViewGroup.LayoutParams(800, 800));
                chart.setUsePercentValues(false);
                chart.getDescription().setEnabled(false);
                chart.getLegend().setTextSize(24f);
                chart.setDrawHoleEnabled(true);
                chart.setHoleColor(Color.WHITE);
                chart.setHoleRadius(40f);
                chart.setDrawCenterText(true);
                chart.setCenterText("Overall\nAttendance");
                chart.setCenterTextSize(24f);

                java.util.List<PieEntry> entries = new java.util.ArrayList<>();
                if (present > 0) entries.add(new PieEntry(present, "Present"));
                if (absent > 0) entries.add(new PieEntry(absent, "Absent"));
                
                if (present == 0 && absent == 0) entries.add(new PieEntry(1, "No Data"));

                PieDataSet dataSet = new PieDataSet(entries, "");
                dataSet.setColors(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"), Color.DKGRAY);
                
                PieData data = new PieData(dataSet);
                data.setValueTextSize(28f);
                data.setValueTextColor(Color.WHITE);
                chart.setData(data);

                // Render off-screen
                chart.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                              View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
                chart.layout(0, 0, 800, 800);

                Bitmap bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                chart.draw(canvas);

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                result[0] = stream.toByteArray();
            } catch (Exception e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        if (error[0] != null) throw error[0];
        return result[0];
    }
}