package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AppStateTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `dashboard and due text recompute after midnight without a database write`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 1)
        val clock = MutableClock(Instant.parse("2026-09-01T12:00:00Z"), zone)
        val daySignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val db = DatabaseFactory.openInMemory()
        val repository = BillRepository(db)
        repository.addBill(
            Bill(
                name = "Rent",
                amount = 1_450.0,
                dueDay = dueDate.dayOfMonth,
                recurrence = Recurrence.MONTHLY,
                anchorEpochDay = dueDate.toEpochDay()
            )
        )
        val state = AppState(db, zone, clock, daySignals)

        try {
            val before = withTimeout(5_000) {
                state.dashboard.first { it.loaded && it.asOfDate == dueDate }
            }
            assertEquals(0, before.overdue.size)
            assertEquals(1, before.upcoming.size)
            assertEquals("Due today", Format.relativeDue(before.rows.single().dueDate!!, before.asOfDate))

            clock.currentInstant = Instant.parse("2026-09-02T00:01:00Z")
            daySignals.emit(Unit)

            val after = withTimeout(5_000) {
                state.dashboard.first { it.loaded && it.asOfDate == dueDate.plusDays(1) }
            }
            assertEquals(1, after.overdue.size)
            assertEquals(0, after.upcoming.size)
            assertEquals("1 day overdue", Format.relativeDue(after.rows.single().dueDate!!, after.asOfDate))
        } finally {
            state.close()
        }
    }

    @Test
    fun `mark paid records the supplied amount for a variable bill`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 1)
        val db = DatabaseFactory.openInMemory()
        val repository = BillRepository(db)
        repository.addBill(
            Bill(
                name = "Electric",
                amount = 138.42,
                dueDay = dueDate.dayOfMonth,
                recurrence = Recurrence.MONTHLY,
                isVariableAmount = true,
                amountMin = 90.0,
                amountMax = 190.0,
                anchorEpochDay = dueDate.toEpochDay()
            )
        )
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow()
        )

        try {
            val row = withTimeout(5_000) { state.dashboard.first { it.loaded }.rows.single() }
            state.markPaid(row, amount = 127.31)

            val payment = withTimeout(5_000) {
                state.repository.observePayments().first { it.isNotEmpty() }.single()
            }
            assertEquals(127.31, payment.amount, 0.001)
        } finally {
            state.close()
        }
    }

    private class MutableClock(
        var currentInstant: Instant,
        private val zoneId: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock =
            if (zone == zoneId) this else MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant
    }
}
