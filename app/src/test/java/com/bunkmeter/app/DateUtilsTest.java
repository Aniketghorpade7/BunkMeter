package com.bunkmeter.app;

import static org.junit.Assert.assertEquals;

import com.bunkmeter.app.utils.DateUtils;

import org.junit.Test;

import java.util.Calendar;

/**
 * Host-side unit tests for {@link DateUtils}.
 *
 * Runs on the plain JVM (no emulator) because DateUtils has no Android
 * dependencies. Guards the day-of-week mapping that caused the original
 * "Saturday lectures never show" bug.
 */
public class DateUtilsTest {

    @Test
    public void weekdays_mapToZeroBasedMondayFirst() {
        assertEquals(0, DateUtils.calendarToAppDay(Calendar.MONDAY));
        assertEquals(1, DateUtils.calendarToAppDay(Calendar.TUESDAY));
        assertEquals(2, DateUtils.calendarToAppDay(Calendar.WEDNESDAY));
        assertEquals(3, DateUtils.calendarToAppDay(Calendar.THURSDAY));
        assertEquals(4, DateUtils.calendarToAppDay(Calendar.FRIDAY));
    }

    @Test
    public void saturday_mapsToFive_notMinusOne() {
        // This is the exact regression: HomeViewModel used to return -1 here,
        // so Saturday lectures never appeared on the Home screen.
        assertEquals(5, DateUtils.calendarToAppDay(Calendar.SATURDAY));
    }

    @Test
    public void sunday_hasNoLectures() {
        assertEquals(DateUtils.SUNDAY_NO_LECTURES, DateUtils.calendarToAppDay(Calendar.SUNDAY));
        assertEquals(-1, DateUtils.calendarToAppDay(Calendar.SUNDAY));
    }

    @Test
    public void todayDateString_isIsoFormat() {
        // yyyy-MM-dd, e.g. 2026-06-25
        assertEquals(10, DateUtils.todayDateString().length());
        assertEquals('-', DateUtils.todayDateString().charAt(4));
        assertEquals('-', DateUtils.todayDateString().charAt(7));
    }

    @Test
    public void nowInMinutes_isWithinADay() {
        int m = DateUtils.nowInMinutes();
        assertEquals(true, m >= 0 && m < 24 * 60);
    }
}
