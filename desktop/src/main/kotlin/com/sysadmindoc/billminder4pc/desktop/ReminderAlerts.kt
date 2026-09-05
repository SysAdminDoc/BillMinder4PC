package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.privacy.PrivacyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    val scheduledAt: Instant,
    /** 0 first showing, 1 the four-hour follow-up, 2 the final one a day after dismissal. */
    val escalationLevel: Int = 0,
    /** When the user first dismissed this reminder. Both follow-ups are measured from it. */
    val dismissedAt: Instant? = null
) {
    val id: ReminderAlertId get() = ReminderAlertId(billId, cycleKey, kind)

    /** Label for a reminder the user has already pushed away once. */
    val escalationLabel: String?
        get() = when (escalationLevel) {
            1 -> "Follow-up"
            2 -> "Still unpaid"
            else -> null
        }

    /** Balloon and window headline. The amount belongs beside the name, the way the phone does it. */
    fun title(): String {
        val suffix = if (isAutoPay) " (auto-pay)" else ""
        return "$billName · ${Format.money(amount, currency)}$suffix"
    }

    /**
     * Headline for a notification, which outlives the moment on the lock screen and in the Action
     * Center history. Masked, it names neither the bill nor the amount.
     */
    fun externalTitle(masked: Boolean): String =
        if (masked) {
            PrivacyText.HIDDEN_BILL_NAME
        } else {
            title()
        }

    /** Body for a notification. The due state is safe; the amount is not. */
    fun externalBody(today: LocalDate, masked: Boolean): String =
        if (masked) PrivacyText.HIDDEN_EXTERNAL_AMOUNT else body(today)

    /** Plain-language due state, sharing one wording with the ledger. */
    fun body(today: LocalDate): String = Format.relativeDue(cycleDate, today)
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

/** Hours after the first dismissal at which each follow-up is raised. */
private const val FOLLOW_UP_HOURS = 4L
private const val FINAL_HOURS = 24L

/**
 * The next step of the dismissal cascade, or null when the reminder is spent.
 *
 * Both follow-ups are measured from the first dismissal, so pushing the four-hour one away late
 * does not push the last one a further day out. An overdue reminder does not cascade: it is
 * already the end of the line, and a reminder the user cannot clear is a reminder they learn to
 * ignore. Neither does an auto-paying bill, which was never asking the user to do anything.
 */
fun ReminderAlert.escalate(now: Instant): Pair<ReminderAlert, Instant>? {
    if (kind == ReminderKind.OVERDUE || isAutoPay) return null
    val firstDismissal = dismissedAt ?: now
    val wakeAt = when (escalationLevel) {
        0 -> firstDismissal.plus(FOLLOW_UP_HOURS, ChronoUnit.HOURS)
        1 -> firstDismissal.plus(FINAL_HOURS, ChronoUnit.HOURS)
        else -> return null
    }
    // A follow-up left until after its successor was already due has nothing left to say.
    if (!wakeAt.isAfter(now)) return null
    return copy(
        escalationLevel = escalationLevel + 1,
        dismissedAt = firstDismissal
    ) to wakeAt
}

/**
 * Holds reminders that are waiting for the user.
 *
 * Deliberately free of timers. A snooze records the wall-clock instant it should reappear and
 * [tick] promotes it, because Windows relative timers do not count Modern Standby time and a
 * one-hour snooze taken before the machine sleeps has to fire on wake, not an hour after it.
 *
 * Every mutator is synchronised. Dismiss and snooze arrive on the AWT event thread from the
 * reminder pane while submit, tick and settle arrive from three separate coroutines, and each is a
 * read-modify-write across the same three collections. Unlocked, a dismissal racing a tick puts a
 * terminally dismissed reminder back on screen, and a defer racing a tick drops a snoozed one that
 * the scheduler will never emit again.
 */
class ReminderAlertQueue {

    private val lock = Any()

    private val _pending = MutableStateFlow<List<ReminderAlert>>(emptyList())
    val pending: StateFlow<List<ReminderAlert>> = _pending.asStateFlow()

    private val _current = MutableStateFlow<ReminderAlert?>(null)
    val current: StateFlow<ReminderAlert?> = _current.asStateFlow()

    private val snoozed = mutableMapOf<ReminderAlertId, SnoozedAlert>()
    private val handled = mutableSetOf<ReminderAlertId>()

    private data class SnoozedAlert(val alert: ReminderAlert, val wakeAt: Instant)

    /** Queues [alert] unless it is already waiting, snoozed, or was dismissed in this session. */
    fun submit(alert: ReminderAlert) {
        synchronized(lock) {
            val id = alert.id
            if (id in handled || id in snoozed) return
            if (_pending.value.any { it.id == id }) return
            _pending.value = _pending.value + alert
            publish()
        }
    }

    /** Removes the reminder for good. It will not return until the app restarts. */
    fun dismiss(id: ReminderAlertId) {
        synchronized(lock) {
            handled += id
            snoozed.remove(id)
            _pending.value = _pending.value.filterNot { it.id == id }
            publish()
        }
    }

    /** Hides the reminder until [wakeAt]. A later [tick] brings it back. */
    fun snooze(id: ReminderAlertId, wakeAt: Instant) {
        synchronized(lock) {
            val alert = _pending.value.firstOrNull { it.id == id } ?: return
            deferLocked(alert, wakeAt)
        }
    }

    /**
     * Holds [alert] until [wakeAt], replacing whatever version of it is waiting now. Escalation
     * uses this to put back a reminder the user dismissed, carrying its new level.
     */
    fun defer(alert: ReminderAlert, wakeAt: Instant) {
        synchronized(lock) {
            deferLocked(alert, wakeAt)
        }
    }

    private fun deferLocked(alert: ReminderAlert, wakeAt: Instant) {
        val id = alert.id
        if (id in handled) return
        snoozed[id] = SnoozedAlert(alert, wakeAt)
        _pending.value = _pending.value.filterNot { it.id == id }
        publish()
    }

    /** Promotes every snoozed reminder whose wake time has arrived. */
    fun tick(now: Instant) {
        synchronized(lock) {
            val due = snoozed.filterValues { !it.wakeAt.isAfter(now) }
            if (due.isEmpty()) return
            due.keys.forEach(snoozed::remove)
            _pending.value = _pending.value + due.values.map { it.alert }
            publish()
        }
    }

    /**
     * Reconciles every waiting reminder against the ledger.
     *
     * Drops reminders whose cycle has been paid or whose bill is gone, and refreshes the ones that
     * survive from the live bill. An alert is a snapshot taken when the event fired, and a snoozed
     * or escalating one can be a day old, so without this the pane could name an amount that mark
     * paid would not write.
     */
    fun settle(bills: List<Bill>, paidCycleKeys: Map<Long, Set<String>>) {
        synchronized(lock) {
            val billsById = bills.associateBy { it.id }

            fun refreshed(alert: ReminderAlert): ReminderAlert? {
                val bill = billsById[alert.billId] ?: return null
                if (alert.cycleKey in paidCycleKeys[alert.billId].orEmpty()) return null
                return alert.copy(
                    billName = bill.name,
                    amount = bill.amount,
                    currency = bill.currency,
                    isVariableAmount = bill.isVariableAmount,
                    isAutoPay = bill.isAutoPay
                )
            }

            var changed = false
            snoozed.keys.toList().forEach { id ->
                val held = snoozed.getValue(id)
                val next = refreshed(held.alert)
                if (next == null) {
                    snoozed.remove(id)
                    changed = true
                } else if (next != held.alert) {
                    snoozed[id] = held.copy(alert = next)
                    changed = true
                }
            }

            val kept = _pending.value.mapNotNull(::refreshed)
            if (kept != _pending.value) {
                _pending.value = kept
                changed = true
            }
            if (changed) publish()
        }
    }

    private fun publish() {
        _current.value = _pending.value.firstOrNull()
    }
}
