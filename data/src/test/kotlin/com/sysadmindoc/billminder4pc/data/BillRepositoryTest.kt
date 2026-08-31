package com.sysadmindoc.billminder4pc.data

import com.sysadmindoc.billminder4pc.core.cycle.BillCycles
import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BillRepositoryTest {

    private lateinit var db: BillDatabase
    private lateinit var repo: BillRepository

    private val zone: ZoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        db = DatabaseFactory.openInMemory()
        repo = BillRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Anchored at its first occurrence, so March is the earliest cycle that can be outstanding. */
    private fun monthlyBill(day: Int = 15) = Bill(
        name = "Rent",
        amount = 1450.0,
        dueDay = day,
        recurrence = Recurrence.MONTHLY,
        anchorEpochDay = LocalDate.of(2026, 3, day).toEpochDay()
    )

    @Test
    fun `insert assigns an id and stores the bill`() = runTest {
        val id = repo.addBill(monthlyBill())
        assertTrue(id > 0)

        val stored = repo.bill(id)
        assertNotNull(stored)
        assertEquals("Rent", stored!!.name)
        assertEquals(1450.0, stored.amount, 0.001)
    }

    @Test
    fun `every stored bill carries an anchor even when none was supplied`() = runTest {
        val id = repo.addBill(monthlyBill().copy(anchorEpochDay = 0L))
        val stored = repo.bill(id)
        assertNotNull(stored)
        assertTrue("normalize should have filled the anchor", stored!!.anchorEpochDay > 0L)
    }

    @Test
    fun `marking paid settles exactly the requested occurrence`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!
        val due = LocalDate.of(2026, 3, 15)

        repo.markPaid(bill, due, zone = zone)

        val payments = repo.paymentsFor(id)
        assertEquals(1, payments.size)
        assertEquals(CycleEngine.cycleKey(due), payments.first().cycleKey)

        val resolved = BillCycles.resolve(bill, payments, today = due, zone = zone)
        assertNotNull(resolved)
        assertTrue(resolved!!.isPaid)
        assertFalse(resolved.isOverdue)
    }

    @Test
    fun `marking the same occurrence twice does not duplicate the payment`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!
        val due = LocalDate.of(2026, 3, 15)

        repo.markPaid(bill, due, zone = zone)
        repo.markPaid(bill, due, amount = 1500.0, zone = zone)

        val payments = repo.paymentsFor(id)
        assertEquals("the unique index should collapse this to one row", 1, payments.size)
        assertEquals(1500.0, payments.first().amount, 0.001)
    }

    @Test
    fun `undo removes only that occurrence`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!
        repo.markPaid(bill, LocalDate.of(2026, 3, 15), zone = zone)
        repo.markPaid(bill, LocalDate.of(2026, 4, 15), zone = zone)

        repo.undoPaid(id, LocalDate.of(2026, 3, 15))

        val remaining = repo.paymentsFor(id)
        assertEquals(1, remaining.size)
        assertEquals("2026-04-15", remaining.first().cycleKey)
    }

    @Test
    fun `deleting a bill cascades to its payments`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!
        repo.markPaid(bill, LocalDate.of(2026, 3, 15), zone = zone)

        repo.deleteBill(id)

        assertEquals(0, repo.paymentsFor(id).size)
        assertEquals(null, repo.bill(id))
    }

    @Test
    fun `an unpaid past occurrence resolves as overdue`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!

        val resolved = BillCycles.resolve(
            bill,
            payments = emptyList(),
            today = LocalDate.of(2026, 3, 20),
            zone = zone
        )

        assertNotNull(resolved)
        assertTrue(resolved!!.isOverdue)
        assertFalse(resolved.isPaid)
    }

    /**
     * Paying a later cycle must not bury an earlier unpaid one. The engine deliberately stays on
     * the oldest outstanding occurrence rather than rolling forward to the one just settled.
     */
    @Test
    fun `paying a later cycle leaves the older unpaid one current`() = runTest {
        val id = repo.addBill(monthlyBill())
        val bill = repo.bill(id)!!
        repo.markPaid(bill, LocalDate.of(2026, 4, 15), zone = zone)

        val resolved = BillCycles.resolve(
            bill,
            repo.paymentsFor(id),
            today = LocalDate.of(2026, 4, 20),
            zone = zone
        )

        assertNotNull(resolved)
        assertEquals("2026-03-15", resolved!!.cycleKey)
        assertTrue(resolved.isOverdue)
        assertFalse(resolved.isPaid)
    }
}
