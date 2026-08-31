package com.sysadmindoc.billminder4pc.data

import androidx.room3.withWriteTransaction
import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.BillPayee
import com.sysadmindoc.billminder4pc.core.model.Payment
import com.sysadmindoc.billminder4pc.core.model.PayeeDraft
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only writer the rest of the app talks to. Every bill write runs through
 * [CycleEngine.normalize] so no row reaches the database without a stored anchor date.
 */
class BillRepository(private val db: BillDatabase) {

    private val dao: BillDao get() = db.billDao()

    fun observeBills(): Flow<List<Bill>> = dao.observeBills()

    fun observePayments(): Flow<List<Payment>> = dao.observePayments()

    fun observePayees(): Flow<List<BillPayee>> = dao.observePayees()

    suspend fun bill(id: Long): Bill? = dao.bill(id)

    suspend fun paymentsFor(billId: Long): List<Payment> = dao.paymentsFor(billId)

    suspend fun addBill(bill: Bill, payees: List<PayeeDraft> = emptyList()): Long =
        db.withWriteTransaction {
            val id = dao.insertBill(CycleEngine.normalize(bill))
            if (payees.isNotEmpty()) {
                dao.insertPayees(payees.map { BillPayee(billId = id, name = it.name, sharePercent = it.sharePercent) })
            }
            id
        }

    suspend fun updateBill(bill: Bill, payees: List<PayeeDraft>? = null) {
        db.withWriteTransaction {
            dao.updateBill(CycleEngine.normalize(bill))
            if (payees != null) {
                dao.replacePayees(
                    bill.id,
                    payees.map { BillPayee(billId = bill.id, name = it.name, sharePercent = it.sharePercent) }
                )
            }
        }
    }

    suspend fun deleteBill(id: Long) = dao.deleteBillById(id)

    /**
     * Settles one occurrence. The cycle key is derived here rather than taken from the caller, so a
     * stale screen cannot mark the wrong occurrence paid.
     */
    suspend fun markPaid(
        bill: Bill,
        date: LocalDate,
        amount: Double = bill.amount,
        note: String = "",
        confirmationNumber: String = "",
        zone: ZoneId = ZoneId.systemDefault()
    ): Long = dao.insertPayment(
        Payment(
            billId = bill.id,
            amount = amount,
            dueDate = CycleEngine.dueInstant(date, zone),
            note = note,
            confirmationNumber = confirmationNumber,
            currency = bill.currency,
            cycleKey = CycleEngine.cycleKey(date)
        )
    )

    suspend fun undoPaid(billId: Long, date: LocalDate) =
        dao.deletePayment(billId, CycleEngine.cycleKey(date))
}
