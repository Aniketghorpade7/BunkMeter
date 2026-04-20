package com.bunkmeter.app.model;

/**
 * Strongly-typed enum for attendance status.
 *
 * The {@code value} field maps 1-to-1 to the existing SQLite INTEGER column
 * so NO database migration is required.  Raw int callers (ExcelGenerator,
 * LectureAdapter, AttendanceDao SQL queries) continue to work via
 * {@link Attendance#getStatus()} which still returns the plain int.
 *
 * New code should always use this enum rather than magic numbers.
 */
public enum AttendanceStatus {

    /** Student was absent / bunked the lecture. DB value = 0. */
    BUNK(0),

    /** Student attended the lecture. DB value = 1. */
    PRESENT(1),

    /** Lecture was cancelled (holiday / no teacher). DB value = 2. */
    CANCELLED(2);

    /** The raw integer that is stored in the {@code Attendance.status} column. */
    public final int value;

    AttendanceStatus(int value) {
        this.value = value;
    }

    /**
     * Converts a raw DB integer back into its enum constant.
     * Falls back to {@link #BUNK} for any unrecognised value.
     */
    public static AttendanceStatus fromInt(int v) {
        for (AttendanceStatus s : values()) {
            if (s.value == v) return s;
        }
        return BUNK;
    }
}
