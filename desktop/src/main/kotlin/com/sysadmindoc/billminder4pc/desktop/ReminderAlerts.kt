package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * Identity of a reminder the user has seen. Matches the scheduler's own event identity so a
 * reminder that was already delivered is never queued twice for the same cycle.
 */
data class ReminderAlertId(
    val billId: Long,
    val cycleKey: String,
    val kind: ReminderKind
)

/** A reminder that is ready to be shown, flattened so the window needs no database access. */
data class ReminderAlert(
    val billId: Long,
    val billName: String,
    val amount: Double,
    val currency: String,
    val isVariableAmount: Boolean,
    val isAutoPay: Boolean,
    val cycleDate: LocalDate,
    val cycleKey: String,
    val kind: ReminderKind,
    val scheduledAt: Instant
) {
    val id: ReminderAlertId get() = ReminderAlertId(billId, cycleKey, kind)

    /** Balloon and window headline. The amount belongs beside the name, the way the phone does it. */
    fun title(hideAmount: Boolean = false): String {
        val money = if (hideAmount) HIDDEN_AMOUNT else Format.money(amount, currency)
        val suffix = if (isAutoPay) " (auto-pay)" else ""
        return "$billName · $money$suffix"
    }

    /** Plain-language due state, sharing one wording with the ledger. */
    fun body(today: LocalDate): String = Format.relativeDue(cycleDate, today)

    companion object {
        const val HIDDEN_AMOUNT = "••••"
    }
}

fun ReminderEvent.toAlert(): ReminderAlert = ReminderAlert(
    billId = bill.id,
    billName = bill.name,
    amount = bill.amount,
    currency = bill.currency,
    isVariableAmount = bill.isVariableAmount,
    isAutoPay = bill.isAutoPay,
    cycleDate = cycleDate,
    cycleKey = cycleDate.toString(),
    kind = kind,
    scheduledAt = scheduledAt
)

/** Snooze offsets offered on a reminder, in the order the window presents them. */
enum class SnoozeChoice(val label: String) {
    ONE_HOUR("Snooze 1 hour"),
    TOMORROW("Snooze until tomorrow")
}

/**
 * Holds reminders that are waiting for the user.
 *
 * Deliberately free of timers. A snooze records the wall-clock instant it should reappear and
 * [tick] promotes it, because Windows relative timers do not count Modern Standby time and a
 * one-hour snooze taken before the machine sleeps has to fire on wake, not an hour after it.
 */
class ReminderAlertQueue {

    private val _pending = MutableStateFlow<List<ReminderAlert>>(emptyList())
    val pending: StateFlow<List<ReminderAlert>> = _pending.asStateFlow()

    private val _current = MutableStateFlow<ReminderAlert?>(null)
    val current: StateFlow<ReminderAlert?> = _current.asStateFlow()

    private val snoozed = mutableMapOf<ReminderAlertId, SnoozedAlert>()
    private val handled = mutableSetOf<ReminderAlertId>()

    private data class SnoozedAlert(val alert: ReminderAlert, val wakeAt: Instant)

    /** Queues [alert] unless it is already waiting, snoozed, or was dismissed in this session. */
    fun submit(alert: ReminderAlert) {
        val id = alert.id
        if (id in handled || id in snoozed) return
        if (_pending.value.any { it.id == id }) return
        _pending.value = _pending.value + alert
        publish()
    }

    /** Removes the reminder for good. It will not return until the app restarts. */
    fun dismiss(id: ReminderAlertId) {
        handled += id
        snoozed.remove(id)
        _pending.value = _pending.value.filterNot { it.id == id }
        publish()
    }

    /** Hides the reminder until [wakeAt]. A later [tick] brings it back. */
    fun snooze(id: ReminderAlertId, wakeAt: Instant) {
        val alert = _pending.value.firstOrNull { it.id == id } ?: return
        snoozed[id] = SnoozedAlert(alert, wakeAt)
        _pending.value = _pending.value.filterNot { it.id == id }
        publish()
    }

    /** Promotes every snoozed reminder whose wake time has arrived. */
    fun tick(now: Instant) {
        val due = snoozed.filterValues { !it.wakeAt.isAfter(now) }
        if (due.isEmpty()) return
        due.keys.forEach(snoozed::remove)
        _pending.value = _pending.value + due.values.map { it.alert }
        publish()
    }

    /**
     * Drops reminders whose cycle has since been paid, or whose bill is gone. A reminder the user
     * has already acted on must not keep asking.
     */
    fun settle(bills: List<Bill>, paidCycleKeys: Map<Long, Set<String>>) {
        val liveBillIds = bills.mapTo(mutableSetOf()) { it.id }
        fun stale(alert: ReminderAlert): Boolean =
            alert.billId !in liveBillIds || alert.cycleKey in paidCycleKeys[alert.billId].orEmpty()

        val staleSnoozed = snoozed.filterValues { stale(it.alert) }.keys
        staleSnoozed.forEach(snoozed::remove)
        val kept = _pending.value.filterNot(::stale)
        if (kept.size != _pending.value.size || staleSnoozed.isNotEmpty()) {
            _pending.value = kept
            publish()
        }
    }

    private fun publish() {
        _current.value = _pending.value.firstOrNull()
    }
}
