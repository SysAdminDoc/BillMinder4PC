package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.BillRow
import com.sysadmindoc.billminder4pc.desktop.Format
import com.sysadmindoc.billminder4pc.desktop.theme.CatCrust
import com.sysadmindoc.billminder4pc.desktop.theme.CatGreen
import com.sysadmindoc.billminder4pc.desktop.theme.CatRed
import com.sysadmindoc.billminder4pc.desktop.theme.CatSubtext0
import com.sysadmindoc.billminder4pc.desktop.theme.CatYellow
import com.sysadmindoc.billminder4pc.desktop.theme.storedBillColor

@Composable
fun BillsScreen(state: AppState) {
    val dashboard by state.dashboard.collectAsState()
    val errorMessage by state.errorMessage.collectAsState()
    val variablePaymentRow by state.paymentPromptRow.collectAsState()

    if (!dashboard.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading", color = CatSubtext0, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            errorMessage?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CatRed.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                message,
                                color = CatRed,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = state::clearError) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            item { SummaryHeader(dashboard.totalDue, dashboard.paid.size, dashboard.rows.size, dashboard.overdue.size) }

            if (dashboard.overdue.isNotEmpty()) {
                item { SectionHeading("Needs attention", dashboard.overdue.size, CatRed) }
                items(dashboard.overdue, key = { it.bill.id }) {
                    BillCard(it, dashboard.asOfDate, onMarkPaid = state::requestQuickPay, onUndoPaid = state::undoPaid)
                }
            }
            if (dashboard.upcoming.isNotEmpty()) {
                item { SectionHeading("Coming up", dashboard.upcoming.size, MaterialTheme.colorScheme.primary) }
                items(dashboard.upcoming, key = { it.bill.id }) {
                    BillCard(it, dashboard.asOfDate, onMarkPaid = state::requestQuickPay, onUndoPaid = state::undoPaid)
                }
            }
            if (dashboard.paid.isNotEmpty()) {
                item { SectionHeading("Paid", dashboard.paid.size, CatGreen) }
                items(dashboard.paid, key = { it.bill.id }) {
                    BillCard(it, dashboard.asOfDate, onMarkPaid = state::requestQuickPay, onUndoPaid = state::undoPaid)
                }
            }
            if (dashboard.rows.isEmpty()) {
                item {
                    Text(
                        "No bills yet. Import from BillMinder for Android, or add one.",
                        color = CatSubtext0,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            }
        }

        variablePaymentRow?.let { row ->
            var paymentAmountText by remember(row.bill.id) { mutableStateOf(row.bill.amount.toString()) }
            val amount = paymentAmountText.trim().replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(CatCrust.copy(alpha = 0.82f))
                    .pointerInput(row.bill.id) {
                        detectTapGestures { state.dismissPaymentPrompt() }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.width(440.dp)
                        .pointerInput(row.bill.id) { detectTapGestures { } }
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Record ${row.bill.name} payment",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Enter the actual amount paid. The saved estimate is " +
                                Format.money(row.bill.amount, row.bill.currency) + ".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = paymentAmountText,
                            onValueChange = { paymentAmountText = it },
                            label = { Text("Amount") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = amount == null,
                            supportingText = {
                                if (amount == null) Text("Enter a positive amount")
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = state::dismissPaymentPrompt) {
                                Text("Cancel")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = amount != null,
                                onClick = {
                                    state.submitPayment(row, requireNotNull(amount))
                                }
                            ) {
                                Text("Record payment")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(totalDue: Double, paidCount: Int, billCount: Int, overdueCount: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Total due", style = MaterialTheme.typography.labelLarge, color = CatSubtext0)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Format.money(totalDue),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$paidCount of $billCount paid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CatSubtext0
                    )
                }
                if (overdueCount > 0) {
                    Text(
                        if (overdueCount == 1) "1 overdue" else "$overdueCount overdue",
                        style = MaterialTheme.typography.titleMedium,
                        color = CatRed
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (billCount == 0) 0f else paidCount.toFloat() / billCount },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = CatGreen,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {}
            )
        }
    }
}

@Composable
private fun SectionHeading(label: String, count: Int, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.weight(1f))
        Text(
            if (count == 1) "1 bill" else "$count bills",
            style = MaterialTheme.typography.labelMedium,
            color = CatSubtext0
        )
    }
}

@Composable
private fun BillCard(
    row: BillRow,
    today: java.time.LocalDate,
    onMarkPaid: (BillRow) -> Unit,
    onUndoPaid: (BillRow) -> Unit
) {
    val due = row.dueDate
    val accent = when {
        row.isPaid -> CatGreen
        row.isOverdue -> CatRed
        else -> CatYellow
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    due?.let { Format.monthLabel(it) } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Text(
                    due?.dayOfMonth?.toString() ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(3.dp, 34.dp)
                    .background(storedBillColor(row.bill.color), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.bill.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        row.bill.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = CatSubtext0
                    )
                    if (row.bill.isAutoPay) {
                        Text(
                            "AUTO-PAY",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                due?.let {
                    Text(
                        if (row.isPaid) "Paid" else Format.relativeDue(it, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
            }
            Text(
                Format.money(row.bill.amount, row.bill.currency),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { if (row.isPaid) onUndoPaid(row) else onMarkPaid(row) }) {
                Icon(
                    if (row.isPaid) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (row.isPaid) "Mark unpaid" else "Mark paid",
                    tint = if (row.isPaid) CatGreen else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
