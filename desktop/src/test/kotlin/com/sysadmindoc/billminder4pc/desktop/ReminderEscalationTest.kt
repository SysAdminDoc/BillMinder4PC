package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The dismissal cascade. A bill the user waves away is still unpaid, so it asks twice more and
 * then stops, and it gives up entirely the moment the cycle is settled.
 */
class ReminderEscalationTest {

    private val dismissedAt: Instant = Instant.parse("2026-09-05T09:00:00Z")
    private val cycle: LocalDate = LocalDate.of(2026, 9, 5)

    private fun bill(id: Long = 1) = Bill(
        id = id,
        name = "Rent",
        amount = 1450.0,
        dueDay = 5,
        anchorEpochDay = cycle.toEpochDay()
    )

    private fun alert(kind: ReminderKind = ReminderKind.PRIMARY) = ReminderEvent(
        bill = bill(),
        cycleDate = cycle,
        kind = kind,
        scheduledAt = dismissedAt,
        daysBeforeDue = 0
    ).toAlert()

    @Test
    fun `a dismissed reminder returns four hours later`() {
        val (next, wakeAt) = alert().escalate(dismissedAt)!!
        assertEquals(1, next.escalationLevel)
        assertEquals(dismissedAt, next.dismissedAt)
        assertEquals(dismissedAt.plus(4, ChronoUnit.HOURS), wakeAt)
        assertEquals("Follow-up", next.escalationLabel)
    }

    @Test
    fun `the last reminder lands a day after the first dismissal, not a day after the follow-up`() {
        val (followUp, _) = alert().escalate(dismissedAt)!!
        val dismissedLate = dismissedAt.plus(6, ChronoUnit.HOURS)

        val (last, wakeAt) = followUp.escalate(dismissedLate)!!
        assertEquals(2, last.escalationLevel)
        assertEquals(dismissedAt.plus(24, ChronoUnit.HOURS), wakeAt)
        assertEquals("Still unpaid", last.escalationLabel)
    }

    @Test
    fun `the cascade ends after the last reminder`() {
        val (followUp, _) = alert().escalate(dismissedAt)!!
        val (last, _) = followUp.escalate(dismissedAt.plus(4, ChronoUnit.HOURS))!!
        assertNull(last.escalate(dismissedAt.plus(24, ChronoUnit.HOURS)))
    }

    @Test
    fun `a follow-up dismissed past the final hour has nothing left to say`() {
        val (followUp, _) = alert().escalate(dismissedAt)!!
        assertNull(followUp.escalate(dismissedAt.plus(30, ChronoUnit.HOURS)))
    }

    @Test
    fun `an overdue reminder does not cascade`() {
        assertNull(alert(kind = ReminderKind.OVERDUE).escalate(dismissedAt))
    }

    @Test
    fun `the queue holds a follow-up back until its hour arrives`() {
        val queue = ReminderAlertQueue()
        val first = alert()
        queue.submit(first)

        val (followUp, wakeAt) = first.escalate(dismissedAt)!!
        queue.defer(followUp, wakeAt)
        assertNull(queue.current.value)

        queue.tick(wakeAt.minusSeconds(1))
        assertNull(queue.current.value)

        queue.tick(wakeAt)
        assertEquals(1, queue.current.value?.escalationLevel)
    }

    @Test
    fun `paying the bill during the cascade stops it`() {
        val queue = ReminderAlertQueue()
        val first = alert()
        queue.submit(first)
        val (followUp, wakeAt) = first.escalate(dismissedAt)!!
        queue.defer(followUp, wakeAt)

        queue.settle(listOf(bill()), mapOf(1L to setOf(cycle.toString())))

        queue.tick(wakeAt.plus(2, ChronoUnit.HOURS))
        assertNull(queue.current.value)
    }

    @Test
    fun `a reminder dismissed for good is not re-armed by a later defer`() {
        val queue = ReminderAlertQueue()
        val first = alert()
        queue.submit(first)
        queue.dismiss(first.id)

        queue.defer(first, dismissedAt.plus(1, ChronoUnit.HOURS))
        queue.tick(dismissedAt.plus(2, ChronoUnit.HOURS))
        assertNull(queue.current.value)
    }
}
