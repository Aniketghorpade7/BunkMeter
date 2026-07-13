package com.bunkmeter.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Centralised date/time helpers.
 *
 * <h3>Why this class exists</h3>
 * The mapping from Java's {@link Calendar} day constants to BunkMeter's own
 * "0 = Monday … 5 = Saturday" convention used to be copy-pasted into
 * {@code HomeViewModel}, {@code DailySetupWorker} and {@code AttendanceRepository}.
 * The three copies drifted apart — the HomeViewModel copy forgot Saturday and
 * returned -1, so Saturday lectures never appeared on the Home screen even
 * though the background workers fired correctly.
 *
 * Keeping the logic in ONE place means a fix (or a bug) lives in ONE place.
 * This is the "Single Source of Truth" principle.
 */
public final class DateUtils {

    // Private constructor: this is a pure utility class, never instantiated.
    private DateUtils() {}

    /** App convention. Sunday has no lectures, so it maps to -1. */
    public static final int SUNDAY_NO_LECTURES = -1;

    /**
     * Converts a {@link Calendar} day-of-week constant (where SUNDAY = 1,
     * MONDAY = 2 … SATURDAY = 7) into BunkMeter's convention:
     * <pre>0 = Mon, 1 = Tue, 2 = Wed, 3 = Thu, 4 = Fri, 5 = Sat, -1 = Sun</pre>
     *
     * @param calendarDayOfWeek e.g. {@code Calendar.get(Calendar.DAY_OF_WEEK)}
     * @return 0–5 for Mon–Sat, or {@link #SUNDAY_NO_LECTURES} for Sunday.
     */
    public static int calendarToAppDay(int calendarDayOfWeek) {
        switch (calendarDayOfWeek) {
            case Calendar.MONDAY:    return 0;
            case Calendar.TUESDAY:   return 1;
            case Calendar.WEDNESDAY: return 2;
            case Calendar.THURSDAY:  return 3;
            case Calendar.FRIDAY:    return 4;
            case Calendar.SATURDAY:  return 5;   // <-- the line HomeViewModel was missing
            default:                 return SUNDAY_NO_LECTURES; // Sunday
        }
    }

    /** Today's app-convention day (0=Mon … 5=Sat, -1=Sun). */
    public static int todayAppDay() {
        return calendarToAppDay(Calendar.getInstance().get(Calendar.DAY_OF_WEEK));
    }

    /** Current wall-clock time expressed as minutes from midnight (e.g. 9:30 → 570). */
    public static int nowInMinutes() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Today's date as the {@code "yyyy-MM-dd"} string the DB stores. */
    public static String todayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
