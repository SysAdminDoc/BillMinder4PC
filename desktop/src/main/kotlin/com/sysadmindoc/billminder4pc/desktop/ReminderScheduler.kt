package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.cycle.BillCycles
import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Payment
import com.sysadmindoc.billminder4pc.data.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal const val REMINDER_RECONCILE_INTERVAL_MILLIS = 15_000L

data class ReminderPolicy(
    val time: LocalTime = LocalTime.of(9, 0),
    val includeOverdue: Boolean = true
)

enum class ReminderKind {
    PRIMARY,
    SECONDARY,
    OVERDUE
}

data class ReminderEvent(
    val bill: Bill,
    val cycleDate: LocalDate,
    val kind: ReminderKind,
    val scheduledAt: Instant,
    val daysBeforeDue: Int
)

private data class ReminderEventId(
    val billId: Long,
    val cycleDate: LocalDate,
    val kind: ReminderKind
)

internal fun schedulerSignals(): Flow<Unit> = flow {
    while (true) {
        delay(REMINDER_RECONCILE_INTERVAL_MILLIS)
        emit(Unit)
    }
}

internal fun reminderEventsBetween(
    bills: List<Bill>,
    payments: List<Payment>,
    startExclusive: Instant,
    endInclusive: Instant,
    zone: ZoneId,
    policy: ReminderPolicy = ReminderPolicy()
): List<ReminderEvent> {
    if (!endInclusive.isAfter(startExclusive)) return emptyList()

    val paidByBill = BillCycles.paidKeys(payments)
    val eventStartDate = startExclusive.atZone(zone).toLocalDate()
    val eventEndDate = endInclusive.atZone(zone).toLocalDate()

    return bills.asSequence()
        .filter { it.isEnabled }
        .flatMap { bill ->
            // An auto-paying bill is not a task, it is something to check. One notice on the day
            // it is taken, and none of the lead-up, second reminder, or overdue nagging.
            val leadDays = if (bill.isAutoPay) 0 else bill.reminderTiming.days
            val latestLeadDays = if (bill.isAutoPay) {
                0
            } else {
                maxOf(bill.reminderTiming.days, bill.secondReminderTiming?.days ?: 0)
            }
            val occurrenceStart = eventStartDate.minusDays(1)
            val occurrenceEnd = eventEndDate.plusDays(latestLeadDays.toLong())
            BillCycles.unpaidOccurrences(
                bill = bill,
                paidKeys = paidByBill[bill.id].orEmpty(),
                start = occurrenceStart,
                endInclusive = occurrenceEnd,
                zone = zone
            ).asSequence().flatMap { cycleDate ->
                buildList {
                    add(
                        ReminderEvent(
                            bill = bill,
                            cycleDate = cycleDate,
                            kind = ReminderKind.PRIMARY,
                            scheduledAt = reminderInstant(
                                cycleDate.minusDays(leadDays.toLong()),
                                zone,
                                policy.time
                            ),
                            daysBeforeDue = leadDays
                        )
                    )
                    bill.secondReminderTiming
                        ?.takeIf { !bill.isAutoPay && it.days != bill.reminderTiming.days }
                        ?.let { second ->
                            add(
                                ReminderEvent(
                                    bill = bill,
                                    cycleDate = cycleDate,
                                    kind = ReminderKind.SECONDARY,
                                    scheduledAt = reminderInstant(
                                        cycleDate.minusDays(second.days.toLong()),
                                        zone,
                                        policy.time
                                    ),
                                    daysBeforeDue = second.days
                                )
                            )
                        }
                    if (policy.includeOverdue && !bill.isAutoPay) {
                        add(
                            ReminderEvent(
                                bill = bill,
                                cycleDate = cycleDate,
                                kind = ReminderKind.OVERDUE,
                                scheduledAt = reminderInstant(cycleDate.plusDays(1), zone, policy.time),
                                daysBeforeDue = 0
                            )
                        )
                    }
                }.asSequence()
            }
        }
        .filter { event ->
            event.scheduledAt.isAfter(startExclusive) && !event.scheduledAt.isAfter(endInclusive)
        }
        .sortedWith(
            compareBy<ReminderEvent>(
                { it.scheduledAt },
                { it.bill.name.lowercase() },
                { it.bill.id },
                { it.kind.ordinal }
            )
        )
        .toList()
}

private fun reminderInstant(date: LocalDate, zone: ZoneId, time: LocalTime): Instant =
    date.atTime(time).atZone(zone).toInstant()

class ReminderScheduler(
    bills: Flow<List<Bill>>,
    payments: Flow<List<Payment>>,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zone),
    timeSignals: Flow<Unit> = schedulerSignals(),
    policy: Flow<ReminderPolicy> = flowOf(ReminderPolicy()),
    private val logger: AppLogger = AppLogger()
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val delivered = mutableSetOf<ReminderEventId>()
    private val _events = MutableSharedFlow<ReminderEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<ReminderEvent> = _events.asSharedFlow()

    private val _lastCheckedAt = MutableStateFlow<Instant?>(null)
    internal val lastCheckedAt: StateFlow<Instant?> = _lastCheckedAt.asStateFlow()

    init {
        scope.launch {
            try {
                combine(
                    bills,
                    payments,
                    timeSignals.onStart { emit(Unit) },
                    policy
                ) { currentBills, currentPayments, _, currentPolicy ->
                    Triple(currentBills, currentPayments, currentPolicy)
                }.collect { (currentBills, currentPayments, currentPolicy) ->
                    reconcile(currentBills, currentPayments, currentPolicy)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.error("Reminder scheduler stopped", failure)
            }
        }
    }

    private suspend fun reconcile(
        bills: List<Bill>,
        payments: List<Payment>,
        policy: ReminderPolicy
    ) {
        val now = clock.instant()
        val previous = _lastCheckedAt.value
        if (previous == null) {
            _lastCheckedAt.value = now
            return
        }
        if (now.isBefore(previous)) {
            logger.info("Reminder scheduler re-anchored after the wall clock moved backward")
            _lastCheckedAt.value = now
            return
        }

        val gap = Duration.between(previous, now)
        if (gap > Duration.ofMillis(REMINDER_RECONCILE_INTERVAL_MILLIS * 2)) {
            logger.info("Reminder scheduler reconciled a wall-clock gap of ${gap.seconds} seconds")
        }
        _lastCheckedAt.value = now

        reminderEventsBetween(bills, payments, previous, now, zone, policy).forEach { event ->
            val id = ReminderEventId(event.bill.id, event.cycleDate, event.kind)
            if (delivered.add(id)) {
                _events.emit(event)
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}
