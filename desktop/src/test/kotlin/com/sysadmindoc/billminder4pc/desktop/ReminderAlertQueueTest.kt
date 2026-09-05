package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderAlertQueueTest {

    private val base: Instant = Instant.parse("2026-09-05T09:00:00Z")

    private fun bill(id: Long, name: String = "Rent", amount: Double = 1450.0) = Bill(
        id = id,
        name = name,
        amount = amount,
        dueDay = 5,
        anchorEpochDay = LocalDate.of(2026, 9, 5).toEpochDay()
    )

    private fun alert(
        id: Long = 1,
        cycle: LocalDate = LocalDate.of(2026, 9, 5),
        kind: ReminderKind = ReminderKind.PRIMARY
    ) = ReminderEvent(
        bill = bill(id),
        cycleDate = cycle,
        kind = kind,
        scheduledAt = base,
        daysBeforeDue = 0
    ).toAlert()

    @Test
    fun `an event becomes the current alert`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert())
        assertEquals(1L, queue.current.value?.billId)
        assertEquals("2026-09-05", queue.current.value?.cycleKey)
    }

    @Test
    fun `the same reminder is never queued twice`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert())
        queue.submit(alert())
        assertEquals(1, queue.pending.value.size)
    }

    @Test
    fun `different kinds for one cycle are separate reminders`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert(kind = ReminderKind.PRIMARY))
        queue.submit(alert(kind = ReminderKind.OVERDUE))
        assertEquals(2, queue.pending.value.size)
    }

    @Test
    fun `dismissing removes the reminder and it does not come back`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        queue.dismiss(a.id)
        assertNull(queue.current.value)
        queue.submit(a)
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test
    fun `a snoozed reminder returns only once its wake time has passed`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        val wakeAt = base.plus(1, ChronoUnit.HOURS)
        queue.snooze(a.id, wakeAt)
        assertNull(queue.current.value)

        queue.tick(wakeAt.minusSeconds(1))
        assertNull(queue.current.value)

        queue.tick(wakeAt)
        assertEquals(a.id, queue.current.value?.id)
    }

    @Test
    fun `a snooze crossed while the machine slept fires on the next tick`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        queue.snooze(a.id, base.plus(1, ChronoUnit.HOURS))

        // The machine was asleep for six hours; the first tick after waking must deliver.
        queue.tick(base.plus(6, ChronoUnit.HOURS))
        assertEquals(a.id, queue.current.value?.id)
    }

    @Test
    fun `paying the cycle clears a waiting reminder`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        queue.settle(listOf(bill(1)), mapOf(1L to setOf("2026-09-05")))
        assertNull(queue.current.value)
    }

    @Test
    fun `paying the cycle clears a snoozed reminder too`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        queue.snooze(a.id, base.plus(1, ChronoUnit.HOURS))
        queue.settle(listOf(bill(1)), mapOf(1L to setOf("2026-09-05")))

        queue.tick(base.plus(2, ChronoUnit.HOURS))
        assertNull(queue.current.value)
    }

    @Test
    fun `deleting the bill clears its reminder`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert())
        queue.settle(emptyList(), emptyMap())
        assertNull(queue.current.value)
    }

    @Test
    fun `settling refreshes a waiting reminder from the live bill`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert())
        // The bill was edited after the reminder fired. The pane must not name an amount that
        // mark paid would not write.
        queue.settle(listOf(bill(1).copy(name = "Rent (new lease)", amount = 1_600.0)), emptyMap())
        assertEquals("Rent (new lease)", queue.current.value?.billName)
        assertEquals(1_600.0, queue.current.value!!.amount, 0.001)
    }

    @Test
    fun `settling refreshes a snoozed reminder too`() {
        val queue = ReminderAlertQueue()
        val a = alert()
        queue.submit(a)
        val wakeAt = base.plus(1, ChronoUnit.HOURS)
        queue.snooze(a.id, wakeAt)
        queue.settle(listOf(bill(1).copy(amount = 1_600.0)), emptyMap())

        queue.tick(wakeAt)
        assertEquals(1_600.0, queue.current.value!!.amount, 0.001)
    }

    @Test
    fun `concurrent dismissal and tick never resurrect a dismissed reminder`() {
        repeat(200) {
            val queue = ReminderAlertQueue()
            val victim = alert(id = 1, kind = ReminderKind.OVERDUE)
            val other = alert(id = 2)
            queue.submit(victim)
            queue.submit(other)
            queue.snooze(other.id, base.plus(1, ChronoUnit.HOURS))

            val threads = listOf(
                Thread { queue.dismiss(victim.id) },
                Thread { queue.tick(base.plus(2, ChronoUnit.HOURS)) }
            )
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)

            assertTrue(
                "a dismissed reminder came back: ${queue.pending.value.map { it.billId }}",
                queue.pending.value.none { it.id == victim.id }
            )
        }
    }

    @Test
    fun `concurrent defer and tick never lose a snoozed reminder`() {
        repeat(200) {
            val queue = ReminderAlertQueue()
            val held = alert(id = 1)
            val waking = alert(id = 2)
            queue.submit(held)
            queue.submit(waking)
            queue.snooze(waking.id, base)

            val threads = listOf(
                Thread { queue.defer(held, base.plus(4, ChronoUnit.HOURS)) },
                Thread { queue.tick(base.plus(1, ChronoUnit.HOURS)) }
            )
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)

            // The woken reminder must be pending; it is in no other collection and the scheduler
            // will never emit it again.
            assertTrue(
                "a woken reminder vanished: ${queue.pending.value.map { it.billId }}",
                queue.pending.value.any { it.id == waking.id }
            )
            assertEquals(1, queue.pending.value.count { it.id == waking.id })
        }
    }

    @Test
    fun `settling leaves an unrelated reminder alone`() {
        val queue = ReminderAlertQueue()
        queue.submit(alert(id = 1))
        queue.submit(alert(id = 2, cycle = LocalDate.of(2026, 9, 7)))
        queue.settle(listOf(bill(1), bill(2)), mapOf(1L to setOf("2026-09-05")))
        assertEquals(listOf(2L), queue.pending.value.map { it.billId })
    }

    @Test
    fun `reminders queue in arrival order and advance as each is handled`() {
        val queue = ReminderAlertQueue()
        val first = alert(id = 1)
        val second = alert(id = 2)
        queue.submit(first)
        queue.submit(second)
        assertEquals(1L, queue.current.value?.billId)
        queue.dismiss(first.id)
        assertEquals(2L, queue.current.value?.billId)
    }

    @Test
    fun `the title carries the amount and marks an auto-pay bill`() {
        val plain = alert()
        assertTrue(plain.title().startsWith("Rent · "))
        assertTrue(!plain.title().contains("auto-pay"))

        val auto = ReminderEvent(
            bill = bill(3).copy(isAutoPay = true),
            cycleDate = LocalDate.of(2026, 9, 5),
            kind = ReminderKind.PRIMARY,
            scheduledAt = base,
            daysBeforeDue = 0
        ).toAlert()
        assertTrue(auto.title().endsWith(" (auto-pay)"))
    }

    @Test
    fun `the body states the due gap in the ledger's own wording`() {
        val today = LocalDate.of(2026, 9, 5)
        assertEquals("Due today", alert(cycle = today).body(today))
        assertEquals("Due tomorrow", alert(cycle = today.plusDays(1)).body(today))
        assertEquals("2 days overdue", alert(cycle = today.minusDays(2)).body(today))
    }
}
