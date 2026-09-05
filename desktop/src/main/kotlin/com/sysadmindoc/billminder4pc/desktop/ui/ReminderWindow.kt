package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.Format
import com.sysadmindoc.billminder4pc.desktop.ReminderAlert
import com.sysadmindoc.billminder4pc.desktop.ReminderKind
import com.sysadmindoc.billminder4pc.desktop.SnoozeChoice
import com.sysadmindoc.billminder4pc.desktop.theme.privateAmount
import com.sysadmindoc.billminder4pc.desktop.theme.storedBillColor
import java.time.LocalDate

/**
 * The reminder itself. Deliberately a small always-on-top pane rather than a full-screen takeover:
 * a desktop is shared with work the user is in the middle of.
 */
@Composable
fun ReminderPane(
    alert: ReminderAlert,
    today: LocalDate,
    billColor: Long,
    remaining: Int,
    onPay: () -> Unit,
    onSnooze: (SnoozeChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val overdue = alert.kind == ReminderKind.OVERDUE || alert.cycleDate.isBefore(today)
    val accent = if (overdue) ledgerDangerColor() else ledgerWarningColor()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(4.dp, 34.dp)
                        .background(storedBillColor(billColor), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(
                            if (overdue) "Bill overdue" else "Bill due",
                            alert.escalationLabel
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = alert.billName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = privateAmount(alert.amount, alert.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (alert.isVariableAmount) {
                        Text(
                            text = "estimated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = alert.body(today),
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = "Due ${Format.date(alert.cycleDate)}" +
                    if (alert.isAutoPay) " · pays automatically, check it went through" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.weight(1f))

            if (remaining > 0) {
                Text(
                    text = if (remaining == 1) "1 more reminder waiting" else "$remaining more reminders waiting",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    text = if (alert.isVariableAmount) "Record payment" else "Mark paid",
                    onClick = onPay
                )
                ActionButton(
                    text = SnoozeChoice.ONE_HOUR.label,
                    onClick = { onSnooze(SnoozeChoice.ONE_HOUR) },
                    primary = false
                )
                ActionButton(
                    text = SnoozeChoice.TOMORROW.label,
                    onClick = { onSnooze(SnoozeChoice.TOMORROW) },
                    primary = false
                )
                Spacer(Modifier.weight(1f))
                ActionButton(text = "Dismiss", onClick = onDismiss, primary = false)
            }
        }
    }
}
