package com.sysadmindoc.billminder4pc.core.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The first three cases are the vectors BillMinder for Android pins, restated in `java.time`.
 * The two apps have to agree about which day a bill is payable or their reminders drift apart.
 */
class HolidayCalendarTest {

    @Test
    fun `Christmas on a Saturday is observed on the Friday, so the walk lands on Thursday`() {
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2021, 12, 24)))
        assertEquals(
            LocalDate.of(2021, 12, 23),
            HolidayCalendar.previousBusinessDay(LocalDate.of(2021, 12, 25))
        )
    }

    @Test
    fun `Independence Day on a Sunday is observed on the Monday`() {
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2021, 7, 5)))
        assertEquals(
            LocalDate.of(2021, 7, 2),
            HolidayCalendar.previousBusinessDay(LocalDate.of(2021, 7, 4))
        )
    }

    @Test
    fun `an ordinary weekday is left alone`() {
        val weekday = LocalDate.of(2026, 8, 3)
        assertEquals(weekday, HolidayCalendar.previousBusinessDay(weekday))
    }

    @Test
    fun `a Saturday due date walks back to the Friday`() {
        assertEquals(
            LocalDate.of(2026, 9, 4),
            HolidayCalendar.previousBusinessDay(LocalDate.of(2026, 9, 5))
        )
    }

    @Test
    fun `a Sunday due date walks back to the Friday`() {
        assertEquals(
            LocalDate.of(2026, 9, 4),
            HolidayCalendar.previousBusinessDay(LocalDate.of(2026, 9, 6))
        )
    }

    @Test
    fun `a Monday holiday sends the walk back to the previous Friday`() {
        // Labor Day 2026 is Monday 7 September.
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2026, 9, 7)))
        assertEquals(
            LocalDate.of(2026, 9, 4),
            HolidayCalendar.previousBusinessDay(LocalDate.of(2026, 9, 7))
        )
    }

    @Test
    fun `New Year's Day on a Saturday is observed on the last day of the previous year`() {
        // 1 January 2022 was a Saturday, observed Friday 31 December 2021.
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2021, 12, 31)))
    }

    @Test
    fun `Juneteenth counts from 2021 and not before`() {
        assertTrue(HolidayCalendar.isHoliday(LocalDate.of(2021, 6, 19)))
        assertFalse(HolidayCalendar.isHoliday(LocalDate.of(2019, 6, 19)))
    }

    @Test
    fun `the floating holidays land on their real dates`() {
        val holidays = HolidayCalendar.federalHolidays(2026)
        assertTrue("MLK Day", LocalDate.of(2026, 1, 19) in holidays)
        assertTrue("Presidents Day", LocalDate.of(2026, 2, 16) in holidays)
        assertTrue("Memorial Day", LocalDate.of(2026, 5, 25) in holidays)
        assertTrue("Labor Day", LocalDate.of(2026, 9, 7) in holidays)
        assertTrue("Columbus Day", LocalDate.of(2026, 10, 12) in holidays)
        assertTrue("Thanksgiving", LocalDate.of(2026, 11, 26) in holidays)
    }

    @Test
    fun `the walk never exceeds the shift it advertises`() {
        // The scheduler widens its occurrence window by MAX_SHIFT_DAYS, so the walk must fit.
        var date = LocalDate.of(2020, 1, 1)
        val end = LocalDate.of(2032, 1, 1)
        while (date.isBefore(end)) {
            val shift = date.toEpochDay() - HolidayCalendar.previousBusinessDay(date).toEpochDay()
            assertTrue(
                "walk from $date took $shift days, more than ${HolidayCalendar.MAX_SHIFT_DAYS}",
                shift <= HolidayCalendar.MAX_SHIFT_DAYS
            )
            date = date.plusDays(1)
        }
    }
}
