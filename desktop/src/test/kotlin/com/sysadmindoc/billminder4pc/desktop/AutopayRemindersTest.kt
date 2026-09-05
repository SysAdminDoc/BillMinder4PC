package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.core.model.ReminderTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A bill that pays itself does not need chasing. It needs one notice on the day the money leaves,
 * so the user can check it went through.
 */
class AutopayRemindersTest {

    private val zone = ZoneId.of("UTC")
    private val cycle: LocalDate = LocalDate.of(2026, 9, 15)

    private fun bill(autoPay: Boolean) = Bill(
        id = 1,
        name = "Netflix",
        amount = 22.99,
        dueDay = cycle.dayOfMonth,
        recurrence = Recurrence.MONTHLY,
        reminderTiming = ReminderTiming.THREE_DAYS,
        secondReminderTiming = ReminderTiming.DAY_OF,
        isAutoPay = autoPay,
        anchorEpochDay = cycle.toEpochDay()
    )

    /** Every event for the September cycle, whenever it was scheduled. */
    private fun eventsFor(autoPay: Boolean): List<ReminderEvent> = reminderEventsBetween(
        bills = listOf(bill(autoPay)),
        payments = emptyList(),
        startExclusive = Instant.parse("2026-09-01T00:00:00Z"),
        endInclusive = Instant.parse("2026-09-30T00:00:00Z"),
        zone = zone
    ).filter { it.cycleDate == cycle }

    @Test
    fun `an autopay bill gets one notice, on the day it is taken`() {
        val events = eventsFor(autoPay = true)
        assertEquals(1, events.size)
        val only = events.single()
        assertEquals(ReminderKind.PRIMARY, only.kind)
        assertEquals(0, only.daysBeforeDue)
        assertEquals(Instant.parse("2026-09-15T09:00:00Z"), only.scheduledAt)
    }

    @Test
    fun `a manual bill keeps its lead-up, second reminder, and overdue notice`() {
        val kinds = eventsFor(autoPay = false).map { it.kind }
        assertEquals(
            listOf(ReminderKind.PRIMARY, ReminderKind.SECONDARY, ReminderKind.OVERDUE),
            kinds.sortedBy { it.ordinal }
        )
        val primary = eventsFor(autoPay = false).single { it.kind == ReminderKind.PRIMARY }
        assertEquals(3, primary.daysBeforeDue)
        assertEquals(Instant.parse("2026-09-12T09:00:00Z"), primary.scheduledAt)
    }

    @Test
    fun `a weekend due date is reminded against the Friday before it`() {
        // 19 September 2026 is a Saturday, so the bill is payable on Friday the 18th and a
        // three-day lead counts back from there, not from the Saturday.
        val weekendCycle = LocalDate.of(2026, 9, 19)
        val weekendBill = bill(autoPay = false).copy(
            dueDay = weekendCycle.dayOfMonth,
            anchorEpochDay = weekendCycle.toEpochDay()
        )
        val primary = reminderEventsBetween(
            bills = listOf(weekendBill),
            payments = emptyList(),
            startExclusive = Instant.parse("2026-09-01T00:00:00Z"),
            endInclusive = Instant.parse("2026-09-30T00:00:00Z"),
            zone = zone
        ).single { it.cycleDate == weekendCycle && it.kind == ReminderKind.PRIMARY }

        assertEquals(Instant.parse("2026-09-15T09:00:00Z"), primary.scheduledAt)
    }

    @Test
    fun `dismissing an autopay notice ends it rather than starting a cascade`() {
        val alert = ReminderEvent(
            bill = bill(autoPay = true),
            cycleDate = cycle,
            kind = ReminderKind.PRIMARY,
            scheduledAt = Instant.parse("2026-09-15T09:00:00Z"),
            daysBeforeDue = 0
        ).toAlert()

        assertNull(alert.escalate(Instant.parse("2026-09-15T10:00:00Z")))
    }

    @Test
    fun `dismissing a manual notice still starts the cascade`() {
        val alert = ReminderEvent(
            bill = bill(autoPay = false),
            cycleDate = cycle,
            kind = ReminderKind.PRIMARY,
            scheduledAt = Instant.parse("2026-09-12T09:00:00Z"),
            daysBeforeDue = 3
        ).toAlert()

        val next = alert.escalate(Instant.parse("2026-09-12T10:00:00Z"))
        assertEquals(1, next?.first?.escalationLevel)
    }
}
