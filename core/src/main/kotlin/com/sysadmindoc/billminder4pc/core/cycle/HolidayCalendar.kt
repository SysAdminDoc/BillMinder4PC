package com.sysadmindoc.billminder4pc.core.cycle

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * US federal holidays and the previous-business-day walk, so a reminder for a bill due on a
 * Saturday arrives while the bank is still open.
 *
 * **Behavioural port, not a source mirror.** BillMinder for Android implements the same rules on
 * `java.util.Calendar`; this module is `java.time` throughout and a `Calendar` copy would be a
 * foreign body in it. The rules and the expected dates match that app, so do not "fix" the
 * difference by copying the Android file over this one. Any change to the rules belongs in both.
 */
object HolidayCalendar {

    /** How far back the walk can ever reach, over a Thursday and Friday holiday pair. */
    const val MAX_SHIFT_DAYS = 5L

    /**
     * Every federal holiday in [year], including both the actual date and the weekday it is
     * observed on when it falls at a weekend. Adjacent years are considered because a New Year's
     * Day on a Saturday is observed on 31 December of the year before.
     */
    fun federalHolidays(year: Int): Set<LocalDate> {
        val holidays = sortedSetOf<LocalDate>()
        for (holidayYear in (year - 1)..(year + 1)) {
            fixedHolidays(holidayYear).forEach { actual ->
                addIfIn(holidays, actual, year)
                addIfIn(holidays, observed(actual), year)
            }
            floatingHolidays(holidayYear).forEach { addIfIn(holidays, it, year) }
        }
        return holidays
    }

    fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun isHoliday(date: LocalDate): Boolean = date in federalHolidays(date.year)

    fun isNonBusinessDay(date: LocalDate): Boolean = isWeekend(date) || isHoliday(date)

    /** [date] itself when it is a business day, otherwise the last business day before it. */
    fun previousBusinessDay(date: LocalDate): LocalDate {
        var candidate = date
        while (isNonBusinessDay(candidate)) {
            candidate = candidate.minusDays(1)
        }
        return candidate
    }

    private fun fixedHolidays(year: Int): List<LocalDate> = buildList {
        add(LocalDate.of(year, 1, 1))
        add(LocalDate.of(year, 7, 4))
        add(LocalDate.of(year, 11, 11))
        add(LocalDate.of(year, 12, 25))
        // Juneteenth became a federal holiday in 2021.
        if (year >= 2021) add(LocalDate.of(year, 6, 19))
    }

    private fun floatingHolidays(year: Int): List<LocalDate> = listOf(
        nth(year, 1, DayOfWeek.MONDAY, 3),      // Martin Luther King Jr Day
        nth(year, 2, DayOfWeek.MONDAY, 3),      // Presidents Day
        last(year, 5, DayOfWeek.MONDAY),        // Memorial Day
        nth(year, 9, DayOfWeek.MONDAY, 1),      // Labor Day
        nth(year, 10, DayOfWeek.MONDAY, 2),     // Columbus Day
        nth(year, 11, DayOfWeek.THURSDAY, 4)    // Thanksgiving
    )

    /** A holiday at the weekend is observed on the nearest weekday. */
    private fun observed(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.minusDays(1)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun nth(year: Int, month: Int, day: DayOfWeek, occurrence: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(occurrence, day))

    private fun last(year: Int, month: Int, day: DayOfWeek): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(day))

    private fun addIfIn(target: MutableSet<LocalDate>, date: LocalDate, year: Int) {
        if (date.year == year) target += date
    }
}
