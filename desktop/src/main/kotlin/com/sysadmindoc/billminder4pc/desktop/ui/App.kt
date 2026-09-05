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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.AppState

enum class Section(val label: String, val icon: ImageVector) {
    BILLS("Bills", Icons.AutoMirrored.Filled.ReceiptLong),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth),
    INSIGHTS("Insights", Icons.Filled.Insights),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun App(
    state: AppState,
    initialSection: Section = Section.BILLS
) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    val errorMessage by state.errorMessage.collectAsState()
    val noticeMessage by state.noticeMessage.collectAsState()
    val paymentPrompt by state.paymentPromptRow.collectAsState()

    // The amount form lives on the Bills page. A reminder for a variable bill can arrive while the
    // user is on Settings, and asking for an amount on a page that cannot show the form is the
    // same as not asking.
    LaunchedEffect(paymentPrompt) {
        if (paymentPrompt != null) section = Section.BILLS
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(current = section, onSelect = { section = it })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    Section.BILLS -> BillsScreen(state)
                    Section.CALENDAR -> CalendarScreen(state)
                    Section.INSIGHTS -> InsightsScreen(state)
                    Section.SETTINGS -> SettingsScreen(state)
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).width(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    errorMessage?.let {
                        MessageCard(it, error = true, onDismiss = state::clearError)
                    }
                    noticeMessage?.let {
                        MessageCard(it, error = false, onDismiss = state::clearNotice)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Section, onSelect: (Section) -> Unit) {
    val success = ledgerSuccessColor()
    Surface(
        modifier = Modifier.width(196.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "BillMinder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
            Text(
                "for PC",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 22.dp)
            )
            Section.entries.forEach { entry ->
                SidebarItem(entry, entry == current) { onSelect(entry) }
            }

            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    "All data is local",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Saved automatically",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 33.dp)
            )
        }
    }
}

@Composable
private fun SidebarItem(section: Section, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = LedgerControlShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)) else null,
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Icon(section.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Text(section.label, style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}
