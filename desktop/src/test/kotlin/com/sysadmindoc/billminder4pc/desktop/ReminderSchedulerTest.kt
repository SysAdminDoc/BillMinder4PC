package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Payment
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.core.model.ReminderTiming
import com.sysadmindoc.billminder4pc.data.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

class ReminderSchedulerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val zone = ZoneId.of("UTC")

    @Test
    fun `scheduler catches a reminder crossed during sleep and does not replay it`() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 1)
        val initial = Instant.parse("2026-08-31T08:58:00Z")
        val clock = MutableClock(initial, zone)
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val bills = MutableStateFlow(listOf(bill(1, "Rent", dueDate)))
        val payments = MutableStateFlow(emptyList<Payment>())
        val directory = temporaryFolder.newFolder("sleep-gap").toPath()
        val logFile = directory.resolve("app.log")
        val scheduler = ReminderScheduler(
            bills = bills,
            payments = payments,
            zone = zone,
            clock = clock,
            timeSignals = ticks,
            logger = AppLogger(logFile, directory.resolve("crash.log"), clock)
        )
        val firstEvent = CompletableDeferred<ReminderEvent>()
        val receivedCount = AtomicInteger()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.events.collect { event ->
                receivedCount.incrementAndGet()
                firstEvent.complete(event)
            }
        }

        try {
            withTimeout(5_000) { scheduler.lastCheckedAt.first { it == initial } }

            clock.currentInstant = Instant.parse("2026-08-31T11:00:00Z")
            ticks.emit(Unit)

            val event = withTimeout(5_000) { firstEvent.await() }
            assertEquals(1L, event.bill.id)
            assertEquals(dueDate, event.cycleDate)
            assertEquals(ReminderKind.PRIMARY, event.kind)
            assertEquals(Instant.parse("2026-08-31T09:00:00Z"), event.scheduledAt)

            clock.currentInstant = Instant.parse("2026-08-31T08:50:00Z")
            ticks.emit(Unit)
            withTimeout(5_000) {
                scheduler.lastCheckedAt.first { it == clock.currentInstant }
            }

            clock.currentInstant = Instant.parse("2026-08-31T09:06:00Z")
            ticks.emit(Unit)
            withTimeout(5_000) {
                scheduler.lastCheckedAt.first { it == clock.currentInstant }
            }
            delay(100)

            assertEquals(1, receivedCount.get())
            val log = Files.readString(logFile)
            assertTrue(log.contains("reconciled a wall-clock gap"))
            assertTrue(log.contains("re-anchored after the wall clock moved backward"))
        } finally {
            collector.cancel()
            scheduler.close()
        }
    }

    @Test
    fun `event calculation skips disabled and already paid cycles`() {
        val dueDate = LocalDate.of(2026, 9, 1)
        val unpaid = bill(1, "Rent", dueDate)
        val paid = bill(2, "Phone", dueDate)
        val disabled = bill(3, "Old subscription", dueDate).copy(isEnabled = false)
        val payment = Payment(
            billId = paid.id,
            amount = paid.amount,
            dueDate = CycleEngine.dueInstant(dueDate, zone),
            cycleKey = CycleEngine.cycleKey(dueDate)
        )

        val events = reminderEventsBetween(
            bills = listOf(unpaid, paid, disabled),
            payments = listOf(payment),
            startExclusive = Instant.parse("2026-08-31T08:59:00Z"),
            endInclusive = Instant.parse("2026-08-31T09:01:00Z"),
            zone = zone
        )

        assertEquals(listOf(1L), events.map { it.bill.id })
        assertEquals(ReminderKind.PRIMARY, events.single().kind)
    }

    @Test
    fun `event calculation includes distinct second and overdue reminders`() {
        val dueDate = LocalDate.of(2026, 9, 1)
        val bill = bill(1, "Rent", dueDate).copy(
            secondReminderTiming = ReminderTiming.DAY_OF
        )

        val events = reminderEventsBetween(
            bills = listOf(bill),
            payments = emptyList(),
            startExclusive = Instant.parse("2026-08-31T08:59:00Z"),
            endInclusive = Instant.parse("2026-09-02T09:01:00Z"),
            zone = zone
        )

        assertEquals(
            listOf(ReminderKind.PRIMARY, ReminderKind.SECONDARY, ReminderKind.OVERDUE),
            events.map { it.kind }
        )
        assertEquals(
            listOf(
                Instant.parse("2026-08-31T09:00:00Z"),
                Instant.parse("2026-09-01T09:00:00Z"),
                Instant.parse("2026-09-02T09:00:00Z")
            ),
            events.map { it.scheduledAt }
        )
    }

    @Test
    fun `reminder policy changes delivery time and can suppress overdue events`() {
        val dueDate = LocalDate.of(2026, 9, 1)
        val bill = bill(1, "Rent", dueDate).copy(secondReminderTiming = ReminderTiming.DAY_OF)

        val events = reminderEventsBetween(
            bills = listOf(bill),
            payments = emptyList(),
            startExclusive = Instant.parse("2026-08-31T16:59:00Z"),
            endInclusive = Instant.parse("2026-09-02T17:01:00Z"),
            zone = zone,
            policy = ReminderPolicy(time = LocalTime.of(17, 0), includeOverdue = false)
        )

        assertEquals(listOf(ReminderKind.PRIMARY, ReminderKind.SECONDARY), events.map { it.kind })
        assertEquals(
            listOf(
                Instant.parse("2026-08-31T17:00:00Z"),
                Instant.parse("2026-09-01T17:00:00Z")
            ),
            events.map { it.scheduledAt }
        )
    }

    private fun bill(id: Long, name: String, dueDate: LocalDate): Bill = Bill(
        id = id,
        name = name,
        amount = 100.0,
        dueDay = dueDate.dayOfMonth,
        recurrence = Recurrence.MONTHLY,
        reminderTiming = ReminderTiming.ONE_DAY,
        anchorEpochDay = dueDate.toEpochDay()
    )

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
