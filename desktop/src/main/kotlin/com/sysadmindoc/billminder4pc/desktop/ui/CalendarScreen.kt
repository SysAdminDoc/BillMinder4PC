package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.cycle.ResolvedCycle
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.BillRow
import com.sysadmindoc.billminder4pc.desktop.Dashboard
import com.sysadmindoc.billminder4pc.desktop.theme.privateAmount
import com.sysadmindoc.billminder4pc.desktop.theme.storedBillColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val calendarMonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
private val agendaDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

internal data class CalendarBill(
    val bill: Bill,
    val date: LocalDate,
    val isPaid: Boolean
)

@Composable
fun CalendarScreen(state: AppState) {
    val dashboard by state.dashboard.collectAsState()
    if (!dashboard.loaded) return

    var displayedMonth by remember(dashboard.asOfDate) { mutableStateOf(YearMonth.from(dashboard.asOfDate)) }
    var selectedDate by remember(dashboard.asOfDate) { mutableStateOf(dashboard.asOfDate) }
    val firstVisibleDate = remember(displayedMonth) {
        val first = displayedMonth.atDay(1)
        first.minusDays((first.dayOfWeek.value % 7).toLong())
    }
    val dates = remember(firstVisibleDate) { List(42) { firstVisibleDate.plusDays(it.toLong()) } }
    val entriesByDate = remember(dashboard.rows, dashboard.paidCycleKeys, firstVisibleDate, state.zone) {
        calendarEntries(dashboard, firstVisibleDate, firstVisibleDate.plusDays(41), state.zone)
    }
    val selectedEntries = entriesByDate[selectedDate].orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageHeader(title = "Calendar", subtitle = "Plan the month at a glance") {
            SquareIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Previous month",
                onClick = {
                    displayedMonth = displayedMonth.minusMonths(1)
                    selectedDate = displayedMonth.atDay(1)
                }
            )
            ActionButton(
                "Today",
                primary = false,
                onClick = {
                    displayedMonth = YearMonth.from(dashboard.asOfDate)
                    selectedDate = dashboard.asOfDate
                }
            )
            Box(Modifier.width(134.dp).height(38.dp), contentAlignment = Alignment.Center) {
                Text(
                    displayedMonth.format(calendarMonthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            SquareIconButton(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Next month",
                onClick = {
                    displayedMonth = displayedMonth.plusMonths(1)
                    selectedDate = displayedMonth.atDay(1)
                }
            )
        }

        LedgerCard(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(30.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT").forEach { day ->
                        Text(
                            day,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(start = 9.dp)
                        )
                    }
                }
                dates.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        week.forEach { date ->
                            CalendarDayCell(
                                date = date,
                                displayedMonth = displayedMonth,
                                selected = date == selectedDate,
                                today = date == dashboard.asOfDate,
                                asOfDate = dashboard.asOfDate,
                                entries = entriesByDate[date].orEmpty(),
                                onClick = { selectedDate = date },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        AgendaStrip(
            selectedDate = selectedDate,
            entries = selectedEntries,
            dashboard = dashboard,
            zone = state.zone,
            onMarkPaid = state::requestQuickPay,
            onUndoPaid = state::undoPaid
        )
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    displayedMonth: YearMonth,
    selected: Boolean,
    today: Boolean,
    asOfDate: LocalDate,
    entries: List<CalendarBill>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inMonth = YearMonth.from(date) == displayedMonth
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        border = BorderStroke(
            if (selected) 1.dp else 0.5.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        inMonth -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                    },
                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Medium
                )
                if (today) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "TODAY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            entries.take(1).forEach { entry ->
                val accent = when {
                    entry.isPaid -> success
                    entry.date.isBefore(asOfDate) -> danger
                    else -> storedBillColor(entry.bill.color)
                }
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        entry.bill.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        privateAmount(entry.bill.amount, entry.bill.currency),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        when {
                            entry.isPaid -> "Paid"
                            entry.date == asOfDate -> "Due today"
                            entry.date.isBefore(asOfDate) -> "Overdue"
                            else -> "Upcoming"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
            }
            if (entries.size > 1) {
                Text(
                    "+${entries.size - 1} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgendaStrip(
    selectedDate: LocalDate,
    entries: List<CalendarBill>,
    dashboard: Dashboard,
    zone: ZoneId,
    onMarkPaid: (BillRow) -> Unit,
    onUndoPaid: (BillRow) -> Unit
) {
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    val warning = ledgerWarningColor()
    LedgerCard(Modifier.fillMaxWidth().height(82.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.width(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedDate == dashboard.asOfDate) warning else MaterialTheme.colorScheme.primary
                )
                Text(
                    selectedDate.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (selectedDate == dashboard.asOfDate) warning else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(46.dp).background(MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(18.dp))

            if (entries.isEmpty()) {
                Text(
                    "No bills on this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val entry = entries.first()
                val row = entry.toBillRow(dashboard, zone)
                Box(
                    Modifier.size(4.dp, 38.dp)
                        .background(storedBillColor(entry.bill.color), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.bill.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            entry.isPaid -> "Paid"
                            selectedDate == dashboard.asOfDate -> "Due today"
                            selectedDate.isBefore(dashboard.asOfDate) -> "Overdue"
                            else -> "Due ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            entry.isPaid -> success
                            selectedDate.isBefore(dashboard.asOfDate) -> danger
                            else -> warning
                        }
                    )
                }
                if (entries.size > 1) {
                    Text(
                        "+${entries.size - 1} more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                Text(
                    privateAmount(entry.bill.amount, entry.bill.currency),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 18.dp)
                )
                BillStatusControl(
                    checked = entry.isPaid,
                    onClick = { if (entry.isPaid) onUndoPaid(row) else onMarkPaid(row) }
                )
            }
        }
    }
}

/**
 * The occurrences the grid shows, keyed by date.
 *
 * Takes the zone rather than reaching for the system default. A bill whose anchor was never
 * normalized derives one from `createdAt`, and that lands on a different date in a different zone,
 * so the grid and the ledger would disagree about the day a bill falls on.
 */
internal fun calendarEntries(
    dashboard: Dashboard,
    start: LocalDate,
    endInclusive: LocalDate,
    zone: ZoneId
): Map<LocalDate, List<CalendarBill>> =
    dashboard.rows.flatMap { row ->
        CycleEngine.occurrencesInRange(row.bill, start, endInclusive, zone).map { date ->
            CalendarBill(
                bill = row.bill,
                date = date,
                isPaid = CycleEngine.cycleKey(date) in dashboard.paidCycleKeys[row.bill.id].orEmpty()
            )
        }
    }.groupBy { it.date }

private fun CalendarBill.toBillRow(dashboard: Dashboard, zone: ZoneId): BillRow {
    val key = CycleEngine.cycleKey(date)
    val payment = dashboard.payments.firstOrNull { it.billId == bill.id && it.cycleKey == key }
    return BillRow(
        bill = bill,
        cycle = ResolvedCycle(
            billId = bill.id,
            date = date,
            cycleKey = key,
            dueAt = CycleEngine.dueInstant(date, zone),
            daysUntilDue = ChronoUnit.DAYS.between(dashboard.asOfDate, date).toInt(),
            isPaid = isPaid,
            isOverdue = !isPaid && date.isBefore(dashboard.asOfDate),
            payment = payment
        )
    )
}
