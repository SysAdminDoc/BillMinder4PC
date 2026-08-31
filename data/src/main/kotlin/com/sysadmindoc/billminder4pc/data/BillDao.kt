package com.sysadmindoc.billminder4pc.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.BillPayee
import com.sysadmindoc.billminder4pc.core.model.Payment
import kotlinx.coroutines.flow.Flow

/**
 * Room on non-Android targets rejects blocking queries, so everything here is either suspending or
 * returns a Flow. That is a constraint rather than a preference.
 */
@Dao
interface BillDao {

    @Query("SELECT * FROM bills ORDER BY name COLLATE NOCASE ASC")
    fun observeBills(): Flow<List<Bill>>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun observePayments(): Flow<List<Payment>>

    @Query("SELECT * FROM bill_payees ORDER BY name COLLATE NOCASE ASC")
    fun observePayees(): Flow<List<BillPayee>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun bill(id: Long): Bill?

    @Query("SELECT * FROM bills")
    suspend fun allBills(): List<Bill>

    @Query("SELECT * FROM payments")
    suspend fun allPayments(): List<Payment>

    @Query("SELECT * FROM bill_payees")
    suspend fun allPayees(): List<BillPayee>

    @Query("SELECT * FROM payments WHERE billId = :billId ORDER BY paidAt DESC")
    suspend fun paymentsFor(billId: Long): List<Payment>

    @Query("SELECT * FROM bill_payees WHERE billId = :billId")
    suspend fun payeesFor(billId: Long): List<BillPayee>

    @Query("SELECT * FROM payments WHERE billId = :billId AND cycleKey = :cycleKey LIMIT 1")
    suspend fun payment(billId: Long, cycleKey: String): Payment?

    @Insert
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBillById(id: Long)

    /**
     * A payment settles one occurrence, and the unique index on (billId, cycleKey) is what enforces
     * that. Replacing on conflict makes a repeated mark-paid idempotent instead of a crash.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Query("DELETE FROM payments WHERE billId = :billId AND cycleKey = :cycleKey")
    suspend fun deletePayment(billId: Long, cycleKey: String)

    @Insert
    suspend fun insertPayees(payees: List<BillPayee>)

    @Query("DELETE FROM bill_payees WHERE billId = :billId")
    suspend fun deletePayeesFor(billId: Long)

    @Transaction
    suspend fun replacePayees(billId: Long, payees: List<BillPayee>) {
        deletePayeesFor(billId)
        if (payees.isNotEmpty()) insertPayees(payees)
    }

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()
}
