package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.BillCategory
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.core.model.ReminderTiming
import com.sysadmindoc.billminder4pc.desktop.AppPreferences
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.BillRow
import com.sysadmindoc.billminder4pc.desktop.Dashboard
import com.sysadmindoc.billminder4pc.desktop.Format
import com.sysadmindoc.billminder4pc.desktop.theme.CategoryColors
import com.sysadmindoc.billminder4pc.desktop.theme.storedBillColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val pageDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)
private val shortDueFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun BillsScreen(state: AppState) {
    val dashboard by state.dashboard.collectAsState()
    val preferences by state.preferences.collectAsState()
    val variablePaymentRow by state.paymentPromptRow.collectAsState()
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    var showAddBill by remember { mutableStateOf(false) }

    if (!dashboard.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = 22.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PageHeader(
                    title = "Bills",
                    subtitle = dashboard.asOfDate.format(pageDateFormatter)
                ) {
                    ActionButton(
                        text = "Add bill",
                        icon = Icons.Filled.Add,
                        onClick = { showAddBill = true }
                    )
                }
            }
            item { BillsSummary(dashboard) }

            if (dashboard.overdue.isNotEmpty()) {
                item { SectionLabel("Needs attention", dashboard.overdue.size, danger) }
                items(dashboard.overdue, key = { "overdue-${it.bill.id}" }) { row ->
                    AttentionBillRow(
                        row = row,
                        today = dashboard.asOfDate,
                        onMarkPaid = state::requestQuickPay
                    )
                }
            }

            if (dashboard.upcoming.isNotEmpty()) {
                item { SectionLabel("Coming up", dashboard.upcoming.size, MaterialTheme.colorScheme.primary) }
                item {
                    BillTable(
                        rows = dashboard.upcoming,
                        today = dashboard.asOfDate,
                        compact = preferences.compactLayout,
                        onMarkPaid = state::requestQuickPay,
                        onUndoPaid = state::undoPaid
                    )
                }
            }

            if (dashboard.paid.isNotEmpty()) {
                item { SectionLabel("Paid this cycle", dashboard.paid.size, success) }
                item {
                    BillTable(
                        rows = dashboard.paid,
                        today = dashboard.asOfDate,
                        compact = preferences.compactLayout,
                        onMarkPaid = state::requestQuickPay,
                        onUndoPaid = state::undoPaid
                    )
                }
            }

            if (dashboard.rows.isEmpty()) {
                item {
                    LedgerCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("No bills yet", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Add your first bill to start the schedule.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ActionButton("Add bill", onClick = { showAddBill = true }, icon = Icons.Filled.Add)
                        }
                    }
                }
            }
        }

        if (showAddBill) {
            AddBillOverlay(
                preferences = preferences,
                onDismiss = { showAddBill = false },
                onSave = {
                    state.addBill(it)
                    showAddBill = false
                }
            )
        }

        variablePaymentRow?.let { row ->
            PaymentAmountOverlay(
                row = row,
                onDismiss = state::dismissPaymentPrompt,
                onSubmit = { amount -> state.submitPayment(row, amount) }
            )
        }
    }
}

@Composable
private fun BillsSummary(dashboard: Dashboard) {
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    LedgerCard(modifier = Modifier.fillMaxWidth().height(112.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(210.dp)) {
                Text("Total due", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    Format.money(dashboard.totalDue),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${Format.money(dashboard.monthTotal)} billed this month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline)
            )
            Column(Modifier.weight(1f).padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${dashboard.paid.size} of ${dashboard.rows.size} paid",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (dashboard.rows.isEmpty()) "0%" else
                            "${(dashboard.paid.size * 100 / dashboard.rows.size)}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = success
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = {
                        if (dashboard.rows.isEmpty()) 0f else dashboard.paid.size.toFloat() / dashboard.rows.size
                    },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = success,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {}
                )
            }
            Box(
                Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline)
            )
            Column(Modifier.width(116.dp).padding(start = 20.dp)) {
                Text(
                    dashboard.overdue.size.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (dashboard.overdue.isEmpty()) success else danger
                )
                Text(
                    if (dashboard.overdue.size == 1) "overdue bill" else "overdue bills",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.weight(1f))
        Text(
            if (count == 1) "1 bill" else "$count bills",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AttentionBillRow(
    row: BillRow,
    today: LocalDate,
    onMarkPaid: (BillRow) -> Unit
) {
    val danger = ledgerDangerColor()
    LedgerCard(
        modifier = Modifier.fillMaxWidth().height(78.dp),
        color = danger.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    row.dueDate?.format(DateTimeFormatter.ofPattern("MMM", Locale.US))?.uppercase() ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = danger
                )
                Text(
                    row.dueDate?.dayOfMonth?.toString() ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    color = danger,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(Modifier.size(4.dp, 36.dp).background(danger, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(row.bill.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    row.dueDate?.let { Format.relativeDue(it, today) } ?: "Date unavailable",
                    style = MaterialTheme.typography.labelMedium,
                    color = danger
                )
            }
            Text(
                Format.money(row.bill.amount, row.bill.currency),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 18.dp)
            )
            BillStatusControl(checked = false, onClick = { onMarkPaid(row) })
        }
    }
}

@Composable
private fun BillTable(
    rows: List<BillRow>,
    today: LocalDate,
    compact: Boolean,
    onMarkPaid: (BillRow) -> Unit,
    onUndoPaid: (BillRow) -> Unit
) {
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DUE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                Text("BILL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("AMOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
                Text("STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(104.dp))
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }
                BillTableRow(row, today, compact, onMarkPaid, onUndoPaid)
            }
        }
    }
}

@Composable
private fun BillTableRow(
    row: BillRow,
    today: LocalDate,
    compact: Boolean,
    onMarkPaid: (BillRow) -> Unit,
    onUndoPaid: (BillRow) -> Unit
) {
    val success = ledgerSuccessColor()
    val warning = ledgerWarningColor()
    val danger = ledgerDangerColor()
    Row(
        modifier = Modifier.fillMaxWidth().height(if (compact) 64.dp else 72.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                row.dueDate?.format(DateTimeFormatter.ofPattern("MMM", Locale.US))?.uppercase() ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = if (row.isPaid) success else warning
            )
            Text(
                row.dueDate?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.headlineSmall,
                color = if (row.isPaid) success else warning,
                fontWeight = FontWeight.Bold
            )
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(4.dp, 34.dp)
                    .background(storedBillColor(row.bill.color), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(row.bill.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    buildString {
                        append(row.bill.category.label)
                        if (row.bill.isAutoPay) append("  AUTO-PAY")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.bill.isAutoPay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    row.dueDate?.let { if (row.isPaid) "Paid" else Format.relativeDue(it, today) } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        row.isPaid -> success
                        row.isOverdue -> danger
                        else -> warning
                    }
                )
            }
        }
        Text(
            Format.money(row.bill.amount, row.bill.currency),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(92.dp)
        )
        BillStatusControl(
            checked = row.isPaid,
            onClick = { if (row.isPaid) onUndoPaid(row) else onMarkPaid(row) }
        )
    }
}

@Composable
internal fun BillStatusControl(checked: Boolean, onClick: () -> Unit) {
    val success = ledgerSuccessColor()
    Surface(
        onClick = onClick,
        modifier = Modifier.width(104.dp).height(36.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = if (checked) success else Color.Transparent,
                border = BorderStroke(1.dp, if (checked) success else MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(20.dp)
            ) {
                if (checked) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                if (checked) "Paid" else "Mark paid",
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) success else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AddBillOverlay(
    preferences: AppPreferences,
    onDismiss: () -> Unit,
    onSave: (Bill) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var category by remember { mutableStateOf(BillCategory.OTHER) }
    var autoPay by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val amount = amountText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val dueDate = runCatching { LocalDate.parse(dueDateText.trim()) }.getOrNull()
    val canSave = name.isNotBlank() && amount != null && dueDate != null

    Overlay(onDismiss) {
        LedgerCard(modifier = Modifier.width(470.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add a bill", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Create a monthly bill. You can mark each cycle paid from the ledger or calendar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        supportingText = { if (amountText.isNotBlank() && amount == null) Text("Enter a positive amount") },
                        isError = amountText.isNotBlank() && amount == null,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text("First due date") },
                        supportingText = { if (dueDate == null) Text("Use YYYY-MM-DD") },
                        isError = dueDate == null,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Box {
                    Surface(
                        onClick = { categoryMenuOpen = true },
                        shape = LedgerControlShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(category.label, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose category")
                        }
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        BillCategory.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    category = option
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatic payment", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Show this bill as an automatic charge.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SquareCheck(autoPay, { autoPay = it }, "Automatic payment")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton("Cancel", onClick = onDismiss, primary = false)
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        "Add bill",
                        enabled = canSave,
                        onClick = {
                            val date = requireNotNull(dueDate)
                            val reminder = ReminderTiming.entries.minByOrNull {
                                kotlin.math.abs(it.days - preferences.firstReminderDays)
                            } ?: ReminderTiming.ONE_DAY
                            onSave(
                                Bill(
                                    name = name.trim(),
                                    amount = requireNotNull(amount),
                                    dueDay = date.dayOfMonth,
                                    dueMonth = date.monthValue - 1,
                                    dueYear = date.year,
                                    category = category,
                                    recurrence = Recurrence.MONTHLY,
                                    isAutoPay = autoPay,
                                    reminderTiming = reminder,
                                    secondReminderTiming = if (preferences.dueDayReminder && reminder != ReminderTiming.DAY_OF) {
                                        ReminderTiming.DAY_OF
                                    } else {
                                        null
                                    },
                                    color = CategoryColors[category.ordinal % CategoryColors.size].value.toLong(),
                                    anchorEpochDay = date.toEpochDay()
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentAmountOverlay(
    row: BillRow,
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var paymentAmountText by remember(row.bill.id) { mutableStateOf(row.bill.amount.toString()) }
    val amount = paymentAmountText.trim().replace(',', '.').toDoubleOrNull()
        ?.takeIf { it.isFinite() && it > 0.0 }

    Overlay(onDismiss) {
        LedgerCard(modifier = Modifier.width(440.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Record ${row.bill.name} payment", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Enter the amount paid. The saved estimate is ${Format.money(row.bill.amount, row.bill.currency)}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = paymentAmountText,
                    onValueChange = { paymentAmountText = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = amount == null,
                    supportingText = { if (amount == null) Text("Enter a positive amount") }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton("Cancel", onClick = onDismiss, primary = false)
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        "Record payment",
                        enabled = amount != null,
                        onClick = { onSubmit(requireNotNull(amount)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Overlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
            content()
        }
    }
}
