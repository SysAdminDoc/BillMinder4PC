package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.cycle.BillCycles
import com.sysadmindoc.billminder4pc.core.cycle.ResolvedCycle
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Payment
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
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
import java.time.LocalTime
import java.time.ZoneId
import java.awt.Desktop
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
    val payments: List<Payment> = emptyList(),
    val paidCycleKeys: Map<Long, Set<String>> = emptyMap(),
    val totalDue: Double = 0.0,
    val asOfDate: LocalDate = LocalDate.MIN,
    val loaded: Boolean = false
)

enum class QuickPayResult {
    PAYMENT_STARTED,
    AMOUNT_REQUIRED,
    IGNORED
}

/**
 * Owns the database and turns it into the one state object the UI renders. Held for the lifetime
 * of the process rather than recreated per window, so opening a second window is cheap.
 */
class AppState(
    private val db: BillDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zone),
    dayChangeSignals: Flow<Unit> = minuteSignals(),
    reminderTickSignals: Flow<Unit> = schedulerSignals(),
    private val preferencesStore: AppPreferencesStore = AppPreferencesStore(),
    private val logger: AppLogger = AppLogger()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository = BillRepository(db)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _noticeMessage = MutableStateFlow<String?>(null)
    val noticeMessage: StateFlow<String?> = _noticeMessage.asStateFlow()

    val preferences: StateFlow<AppPreferences> = preferencesStore.state

    private val _paymentPromptRow = MutableStateFlow<BillRow?>(null)
    val paymentPromptRow: StateFlow<BillRow?> = _paymentPromptRow.asStateFlow()

    private val currentDay = dayChangeSignals
        .onStart { emit(Unit) }
        .map { LocalDate.now(clock.withZone(zone)) }
        .distinctUntilChanged()

    val dashboard: StateFlow<Dashboard> =
        combine(repository.observeBills(), repository.observePayments(), currentDay) { bills, payments, today ->
            buildDashboard(bills, payments, today)
        }.stateIn(scope, SharingStarted.Eagerly, Dashboard())

    private val reminderScheduler = ReminderScheduler(
        bills = repository.observeBills(),
        payments = repository.observePayments(),
        zone = zone,
        clock = clock,
        timeSignals = reminderTickSignals,
        policy = preferences.map {
            ReminderPolicy(
                time = LocalTime.of(it.reminderHour, 0),
                includeOverdue = it.overdueReminders
            )
        },
        logger = logger
    )
    val reminderEvents = reminderScheduler.events

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
            payments = payments,
            paidCycleKeys = paidByBill,
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

    fun requestQuickPay(row: BillRow): QuickPayResult {
        if (row.isPaid || row.dueDate == null) return QuickPayResult.IGNORED
        if (row.bill.isVariableAmount) {
            _paymentPromptRow.value = row
            return QuickPayResult.AMOUNT_REQUIRED
        }
        markPaid(row)
        return QuickPayResult.PAYMENT_STARTED
    }

    fun submitPayment(row: BillRow, amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        _paymentPromptRow.value = null
        markPaid(row, amount)
    }

    fun dismissPaymentPrompt() {
        _paymentPromptRow.value = null
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

    fun clearNotice() {
        _noticeMessage.value = null
    }

    fun updatePreferences(transform: (AppPreferences) -> AppPreferences) {
        preferencesStore.update(transform)
            .onFailure {
                _errorMessage.value = "Couldn't save settings. Details were written to the app log."
            }
    }

    fun addBill(bill: Bill, onSaved: (() -> Unit)? = null) {
        scope.launch {
            try {
                repository.addBill(bill)
                _noticeMessage.value = "${bill.name} was added."
                onSaved?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.error("Add-bill write failed", failure)
                _errorMessage.value = "Couldn't add the bill. Details were written to the app log."
            }
        }
    }

    fun openDataFolder() {
        runCatching {
            check(Desktop.isDesktopSupported()) { "Desktop actions aren't available." }
            Desktop.getDesktop().open(AppPaths.dataDir.toFile())
        }.onFailure {
            logger.error("Opening the data folder failed", it)
            _errorMessage.value = "Couldn't open the data folder."
        }
    }

    fun exportBackup() {
        scope.launch(Dispatchers.IO) {
            var snapshot: java.nio.file.Path? = null
            try {
                val stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val destination = AppPaths.backupsDir.resolve("billminder-backup-$stamp.zip")
                snapshot = AppPaths.backupsDir.resolve("billminder-snapshot-$stamp.db")
                DatabaseFactory.exportSnapshot(db, snapshot)
                val sourceFiles = listOf(snapshot, AppPaths.preferencesFile).filter(Files::exists)
                ZipOutputStream(Files.newOutputStream(destination)).use { output ->
                    sourceFiles.forEach { source ->
                        output.putNextEntry(ZipEntry(source.fileName.toString()))
                        Files.newInputStream(source).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
                val savedAt = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))
                preferencesStore.update { it.copy(lastBackupAt = savedAt) }
                    .getOrThrow()
                _noticeMessage.value = "Backup saved to ${destination.fileName}."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.error("Backup export failed", failure)
                _errorMessage.value = "Couldn't export a backup. Details were written to the app log."
            } finally {
                snapshot?.let { Files.deleteIfExists(it) }
            }
        }
    }

    fun close() {
        reminderScheduler.close()
        scope.cancel()
        db.close()
    }
}
