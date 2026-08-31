package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.cycle.BillCycles
import com.sysadmindoc.billminder4pc.core.cycle.ResolvedCycle
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Payment
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.BillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

private fun minuteSignals(): Flow<Unit> = flow {
    while (true) {
        delay(60_000)
        emit(Unit)
    }
}

/** A bill paired with the occurrence it is currently sitting on. */
data class BillRow(
    val bill: Bill,
    val cycle: ResolvedCycle?
) {
    val isOverdue: Boolean get() = cycle?.isOverdue == true
    val isPaid: Boolean get() = cycle?.isPaid == true
    val dueDate: LocalDate? get() = cycle?.date
}

data class Dashboard(
    val rows: List<BillRow> = emptyList(),
    val overdue: List<BillRow> = emptyList(),
    val upcoming: List<BillRow> = emptyList(),
    val paid: List<BillRow> = emptyList(),
    val totalDue: Double = 0.0,
    val asOfDate: LocalDate = LocalDate.MIN,
    val loaded: Boolean = false
)

/**
 * Owns the database and turns it into the one state object the UI renders. Held for the lifetime
 * of the process rather than recreated per window, so opening a second window is cheap.
 */
class AppState(
    private val db: BillDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zone),
    dayChangeSignals: Flow<Unit> = minuteSignals(),
    private val logger: AppLogger = AppLogger()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository = BillRepository(db)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val currentDay = dayChangeSignals
        .onStart { emit(Unit) }
        .map { LocalDate.now(clock.withZone(zone)) }
        .distinctUntilChanged()

    val dashboard: StateFlow<Dashboard> =
        combine(repository.observeBills(), repository.observePayments(), currentDay) { bills, payments, today ->
            buildDashboard(bills, payments, today)
        }.stateIn(scope, SharingStarted.Eagerly, Dashboard())

    private fun buildDashboard(
        bills: List<Bill>,
        payments: List<Payment>,
        today: LocalDate
    ): Dashboard {
        val paidByBill = BillCycles.paidKeys(payments)
        val rows = bills.map { bill ->
            BillRow(
                bill = bill,
                cycle = BillCycles.resolve(
                    bill = bill,
                    paidKeys = paidByBill[bill.id].orEmpty(),
                    payments = payments,
                    today = today,
                    zone = zone
                )
            )
        }.sortedWith(compareBy({ it.dueDate ?: LocalDate.MAX }, { it.bill.name.lowercase() }))

        val overdue = rows.filter { it.isOverdue }
        val paidRows = rows.filter { it.isPaid }
        val upcoming = rows.filterNot { it.isOverdue || it.isPaid }

        return Dashboard(
            rows = rows,
            overdue = overdue,
            upcoming = upcoming,
            paid = paidRows,
            totalDue = (overdue + upcoming).sumOf { it.bill.amount },
            asOfDate = today,
            loaded = true
        )
    }

    fun markPaid(row: BillRow, amount: Double = row.bill.amount) {
        val date = row.dueDate ?: return
        scope.launch {
            try {
                repository.markPaid(row.bill, date, amount = amount, zone = zone)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.error("Mark-paid write failed for bill ${row.bill.id}", failure)
                _errorMessage.value = "Couldn't record the payment. Details were written to the app log."
            }
        }
    }

    fun undoPaid(row: BillRow) {
        val date = row.dueDate ?: return
        scope.launch {
            try {
                repository.undoPaid(row.bill.id, date)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.error("Undo-payment write failed for bill ${row.bill.id}", failure)
                _errorMessage.value = "Couldn't undo the payment. Details were written to the app log."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun close() {
        scope.cancel()
        db.close()
    }
}
