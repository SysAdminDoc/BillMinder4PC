package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.core.model.ReminderTiming
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the path from a scheduled reminder to something the user can actually act on, which is
 * the whole point of the reminder layer. The scheduler's own timing rules are covered separately.
 */
class ReminderDeliveryTest {

    private val zone = ZoneId.of("UTC")
    private val dueDate: LocalDate = LocalDate.of(2026, 9, 1)

    private fun fixture(
        variable: Boolean = false
    ): Triple<AppState, MutableSharedFlow<Unit>, MutableClock> {
        val clock = MutableClock(Instant.parse("2026-09-01T08:58:00Z"), zone)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
        val db = DatabaseFactory.openInMemory()
        runBlocking {
            BillRepository(db).addBill(
                Bill(
                    name = "Rent",
                    amount = 1_450.0,
                    dueDay = dueDate.dayOfMonth,
                    recurrence = Recurrence.MONTHLY,
                    reminderTiming = ReminderTiming.DAY_OF,
                    isVariableAmount = variable,
                    amountMin = if (variable) 1_000.0 else null,
                    amountMax = if (variable) 2_000.0 else null,
                    anchorEpochDay = dueDate.toEpochDay()
                )
            )
        }
        val state = AppState(
            db = db,
            zone = zone,
            clock = clock,
            dayChangeSignals = emptyFlow(),
            reminderTickSignals = ticks
        )
        return Triple(state, ticks, clock)
    }

    /**
     * Anchors the scheduler, then crosses the reminder time so one event is produced.
     *
     * A tick emitted before the collectors subscribe is dropped, because the signal flow has no
     * replay. Both the scheduler and the snooze ticker read it, so wait for two subscribers
     * rather than letting coroutine start order decide whether the test passes.
     */
    private suspend fun deliverReminder(ticks: MutableSharedFlow<Unit>, clock: MutableClock) {
        withTimeout(5_000) { ticks.subscriptionCount.first { it >= 2 } }
        ticks.emit(Unit)
        clock.currentInstant = Instant.parse("2026-09-01T09:01:00Z")
        ticks.emit(Unit)
    }

    @Test
    fun `a due reminder becomes an alert the user can see`() = runBlocking {
        val (state, ticks, clock) = fixture()
        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)

            val alert = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }
            assertNotNull(alert)
            assertEquals("Rent", alert!!.billName)
            assertEquals(dueDate.toString(), alert.cycleKey)
            assertEquals("Due today", alert.body(dueDate))
        } finally {
            state.close()
        }
    }

    @Test
    fun `marking the alert paid settles that cycle and clears the reminder`() = runBlocking {
        val (state, ticks, clock) = fixture()
        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)
            val alert = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }!!

            assertEquals(QuickPayResult.PAYMENT_STARTED, state.resolveAlert(alert))

            val settled = withTimeout(5_000) {
                state.dashboard.first { snapshot ->
                    snapshot.paidCycleKeys[alert.billId].orEmpty().contains(alert.cycleKey)
                }
            }
            val payment = settled.payments.single()
            assertEquals(alert.cycleKey, payment.cycleKey)
            assertEquals(1_450.0, payment.amount, 0.001)
            assertNull(state.reminderAlerts.current.value)
        } finally {
            state.close()
        }
    }

    @Test
    fun `a variable bill asks for the amount instead of writing the estimate`() = runBlocking {
        val (state, ticks, clock) = fixture(variable = true)
        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)
            val alert = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }!!

            assertEquals(QuickPayResult.AMOUNT_REQUIRED, state.resolveAlert(alert))
            assertEquals(alert.cycleKey, state.paymentPromptRow.value?.cycle?.cycleKey)
            assertTrue(state.dashboard.value.payments.isEmpty())
            assertNotNull(state.reminderAlerts.current.value)
        } finally {
            state.close()
        }
    }

    @Test
    fun `snoozing for an hour hides the alert until the clock passes it`() = runBlocking {
        val (state, ticks, clock) = fixture()
        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)
            val alert = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }!!

            state.snoozeAlert(alert, SnoozeChoice.ONE_HOUR)
            assertNull(state.reminderAlerts.current.value)

            clock.currentInstant = Instant.parse("2026-09-01T09:30:00Z")
            ticks.emit(Unit)
            assertNull(state.reminderAlerts.current.value)

            clock.currentInstant = Instant.parse("2026-09-01T10:05:00Z")
            ticks.emit(Unit)
            val returned = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }
            assertEquals(alert.id, returned!!.id)
        } finally {
            state.close()
        }
    }

    @Test
    fun `paying the bill elsewhere withdraws a waiting reminder`() = runBlocking {
        val (state, ticks, clock) = fixture()
        try {
            val loaded = withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)
            withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }

            state.markPaid(loaded.rows.single())

            withTimeout(5_000) { state.reminderAlerts.current.first { it == null } }
            assertNull(state.reminderAlerts.current.value)
        } finally {
            state.close()
        }
    }

    @Test
    fun `dismissing the alert leaves the bill unpaid`() = runBlocking {
        val (state, ticks, clock) = fixture()
        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            deliverReminder(ticks, clock)
            val alert = withTimeout(5_000) { state.reminderAlerts.current.first { it != null } }!!

            state.dismissAlert(alert)
            assertNull(state.reminderAlerts.current.value)
            assertTrue(state.dashboard.value.payments.isEmpty())
        } finally {
            state.close()
        }
    }

    private class MutableClock(
        var currentInstant: Instant,
        private val zoneId: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
    }
}
