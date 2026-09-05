package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
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

/**
 * What the ledger header claims you owe. "Total due" counted a bill once however many cycles were
 * outstanding, so a bill months in arrears understated the debt by every cycle after the first.
 */
class DashboardTotalsTest {

    private val zone = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 9, 5)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), zone)

    private fun monthly(name: String, amount: Double, anchor: LocalDate) = Bill(
        name = name,
        amount = amount,
        dueDay = anchor.dayOfMonth,
        recurrence = Recurrence.MONTHLY,
        anchorEpochDay = anchor.toEpochDay()
    )

    private suspend fun dashboardFor(
        vararg bills: Bill,
        settle: suspend (BillRepository, BillDatabase) -> Unit = { _, _ -> }
    ): Dashboard {
        val db = DatabaseFactory.openInMemory()
        val repository = BillRepository(db)
        bills.forEach { repository.addBill(it) }
        settle(repository, db)
        val state = AppState(
            db = db,
            zone = zone,
            clock = clock,
            dayChangeSignals = emptyFlow(),
            reminderTickSignals = emptyFlow()
        )
        return try {
            withTimeout(5_000) { state.dashboard.first { it.loaded } }
        } finally {
            state.close()
        }
    }

    @Test
    fun `a bill three cycles behind owes three amounts`() = runBlocking {
        val dashboard = dashboardFor(monthly("Rent", 1_000.0, LocalDate.of(2026, 7, 5)))
        // July, August and September are all outstanding on 5 September.
        assertEquals(3_000.0, dashboard.totalDue, 0.001)
    }

    @Test
    fun `a bill that is merely upcoming owes one amount`() = runBlocking {
        val dashboard = dashboardFor(monthly("Internet", 60.0, LocalDate.of(2026, 9, 20)))
        assertEquals(60.0, dashboard.totalDue, 0.001)
    }

    @Test
    fun `a bill due today owes one amount`() = runBlocking {
        val dashboard = dashboardFor(monthly("Spotify", 12.99, today))
        assertEquals(12.99, dashboard.totalDue, 0.001)
    }

    @Test
    fun `settling one arrears cycle drops the total by exactly that cycle`() = runBlocking {
        val anchor = LocalDate.of(2026, 7, 5)
        val dashboard = dashboardFor(monthly("Rent", 1_000.0, anchor)) { repository, db ->
            val stored = db.billDao().allBills().single()
            repository.markPaid(stored, anchor, amount = 1_000.0, zone = zone)
        }
        assertEquals(2_000.0, dashboard.totalDue, 0.001)
    }

    @Test
    fun `arrears from several bills add up`() = runBlocking {
        val dashboard = dashboardFor(
            monthly("Rent", 1_000.0, LocalDate.of(2026, 8, 5)),
            monthly("Gym", 50.0, LocalDate.of(2026, 9, 1))
        )
        // Rent owes August and September, the gym owes September only.
        assertEquals(2_050.0, dashboard.totalDue, 0.001)
    }

    @Test
    fun `the month total counts what the month bills, including what is already paid`() =
        runBlocking {
            val anchor = LocalDate.of(2026, 9, 1)
            val dashboard = dashboardFor(monthly("Gym", 50.0, anchor)) { repository, db ->
                val stored = db.billDao().allBills().single()
                repository.markPaid(stored, anchor, amount = 50.0, zone = zone)
            }
            assertEquals(50.0, dashboard.monthTotal, 0.001)
            // Paid, so nothing is owed, but the month still billed it.
            assertEquals(0.0, dashboard.totalDue, 0.001)
        }

    @Test
    fun `a weekly bill bills every occurrence in the month, not one`() = runBlocking {
        val weekly = Bill(
            name = "Groceries",
            amount = 100.0,
            dueDay = 2,
            recurrence = Recurrence.WEEKLY,
            anchorEpochDay = LocalDate.of(2026, 9, 1).toEpochDay()
        )
        val dashboard = dashboardFor(weekly)
        // 1, 8, 15, 22 and 29 September.
        assertEquals(500.0, dashboard.monthTotal, 0.001)
    }

    @Test
    fun `a bill with no occurrence this month bills nothing this month`() = runBlocking {
        val yearly = Bill(
            name = "Insurance",
            amount = 900.0,
            dueDay = 14,
            dueMonth = 1,
            dueYear = 2027,
            recurrence = Recurrence.YEARLY,
            anchorEpochDay = LocalDate.of(2027, 2, 14).toEpochDay()
        )
        val dashboard = dashboardFor(yearly)
        assertEquals(0.0, dashboard.monthTotal, 0.001)
    }
}
