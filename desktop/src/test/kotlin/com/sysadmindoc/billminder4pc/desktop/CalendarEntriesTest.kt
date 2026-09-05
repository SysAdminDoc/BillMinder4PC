package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.cycle.BillCycles
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.desktop.ui.calendarEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The grid and the ledger have to agree about the day a bill falls on. They only can if both
 * resolve occurrences in the same zone.
 */
class CalendarEntriesTest {

    private val ledgerZone: ZoneId = ZoneId.of("Pacific/Kiritimati") // UTC+14
    private val otherZone: ZoneId = ZoneId.of("Pacific/Niue") // UTC-11

    /**
     * A bill whose anchor was never normalized. `CycleEngine` then derives one from `createdAt`,
     * and this instant is a different calendar date in the two zones, so the zone actually decides
     * which day the occurrence lands on. Rows written by the repository are normalized, but a row
     * stored before normalization existed keeps the schema default of zero.
     */
    private fun unNormalizedBill() = Bill(
        id = 1,
        name = "Rent",
        amount = 1_000.0,
        dueDay = 15,
        recurrence = Recurrence.MONTHLY,
        createdAt = ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli(),
        anchorEpochDay = 0L
    )

    private fun dashboardOf(bill: Bill, today: LocalDate, zone: ZoneId) = Dashboard(
        rows = listOf(
            BillRow(
                bill = bill,
                cycle = BillCycles.resolve(bill, emptyList(), today, zone)
            )
        ),
        paidCycleKeys = emptyMap(),
        asOfDate = today,
        loaded = true
    )

    @Test
    fun `the grid places a bill on the day the ledger says it is due`() {
        val bill = unNormalizedBill()
        val today = LocalDate.of(2026, 9, 20)
        val dashboard = dashboardOf(bill, today, ledgerZone)
        val ledgerDate = dashboard.rows.single().dueDate!!

        val entries = calendarEntries(
            dashboard = dashboard,
            start = today.minusMonths(1),
            endInclusive = today.plusMonths(1),
            zone = ledgerZone
        )

        val gridDates = entries.values.flatten().map { it.date }
        assertEquals(
            "the ledger's due date is missing from the grid",
            true,
            ledgerDate in gridDates
        )
    }

    @Test
    fun `resolving the grid in the wrong zone moves the bill to a different day`() {
        // The mutation guard: if calendarEntries ignored the zone argument this would not differ,
        // and the first test would pass whether or not the zone was threaded through.
        val bill = unNormalizedBill()
        val today = LocalDate.of(2026, 9, 20)
        val dashboard = dashboardOf(bill, today, ledgerZone)

        fun datesIn(zone: ZoneId) = calendarEntries(
            dashboard = dashboard,
            start = today.minusMonths(1),
            endInclusive = today.plusMonths(1),
            zone = zone
        ).values.flatten().map { it.date }.sorted()

        assertNotEquals(datesIn(ledgerZone), datesIn(otherZone))
    }

    @Test
    fun `a normalized bill lands on the same day whatever the zone`() {
        val bill = unNormalizedBill().copy(anchorEpochDay = LocalDate.of(2026, 9, 15).toEpochDay())
        val today = LocalDate.of(2026, 9, 20)
        val dashboard = dashboardOf(bill, today, ledgerZone)

        fun datesIn(zone: ZoneId) = calendarEntries(
            dashboard = dashboard,
            start = today.minusMonths(1),
            endInclusive = today.plusMonths(1),
            zone = zone
        ).values.flatten().map { it.date }.sorted()

        assertEquals(datesIn(ledgerZone), datesIn(otherZone))
    }
}
