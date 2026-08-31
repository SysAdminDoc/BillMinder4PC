package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AppStateTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

    @Test
    fun `quick pay records a fixed bill without opening the amount prompt`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 1)
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
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow()
        )

        try {
            val row = withTimeout(5_000) { state.dashboard.first { it.loaded }.rows.single() }

            assertEquals(QuickPayResult.PAYMENT_STARTED, state.requestQuickPay(row))
            assertNull(state.paymentPromptRow.value)

            val payment = withTimeout(5_000) {
                state.repository.observePayments().first { it.isNotEmpty() }.single()
            }
            assertEquals(1_450.0, payment.amount, 0.001)
        } finally {
            state.close()
        }
    }

    @Test
    fun `quick pay asks for the exact amount before recording a variable bill`() = runBlocking {
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

            assertEquals(QuickPayResult.AMOUNT_REQUIRED, state.requestQuickPay(row))
            assertEquals(row, state.paymentPromptRow.value)
            assertTrue(state.repository.observePayments().first().isEmpty())

            state.submitPayment(row, 127.31)
            assertNull(state.paymentPromptRow.value)
            val payment = withTimeout(5_000) {
                state.repository.observePayments().first { it.isNotEmpty() }.single()
            }
            assertEquals(127.31, payment.amount, 0.001)
        } finally {
            state.close()
        }
    }

    @Test
    fun `failed payment write reaches the UI state and log`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 1)
        val directory = temporaryFolder.newFolder("write-failure").toPath()
        val logFile = directory.resolve("app.log")
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
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow(),
            logger = AppLogger(logFile, directory.resolve("crash.log"))
        )

        try {
            val row = withTimeout(5_000) { state.dashboard.first { it.loaded }.rows.single() }
            db.close()
            state.markPaid(row)

            val message = withTimeout(5_000) { state.errorMessage.first { it != null } }
            assertEquals("Couldn't record the payment. Details were written to the app log.", message)
            assertTrue(Files.readString(logFile).contains("Mark-paid write failed"))
        } finally {
            state.close()
        }
    }

    @Test
    fun `add bill writes the new row and reports success`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 18)
        val db = DatabaseFactory.openInMemory()
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow()
        )

        try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
            state.addBill(
                Bill(
                    name = "Internet",
                    amount = 74.99,
                    dueDay = dueDate.dayOfMonth,
                    recurrence = Recurrence.MONTHLY,
                    anchorEpochDay = dueDate.toEpochDay()
                )
            )

            val row = withTimeout(5_000) {
                state.dashboard.first { dashboard ->
                    dashboard.rows.any { it.bill.name == "Internet" }
                }.rows.single { it.bill.name == "Internet" }
            }
            assertEquals(74.99, row.bill.amount, 0.001)
            assertEquals("Internet was added.", state.noticeMessage.value)
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
