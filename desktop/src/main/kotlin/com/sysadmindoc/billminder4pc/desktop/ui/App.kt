package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.theme.CatCrust
import com.sysadmindoc.billminder4pc.desktop.theme.CatMantle

enum class Section(val label: String, val icon: ImageVector) {
    BILLS("Bills", Icons.AutoMirrored.Filled.ReceiptLong),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth),
    INSIGHTS("Insights", Icons.Filled.Insights),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun App(state: AppState) {
    var section by remember { mutableStateOf(Section.BILLS) }

    Surface(modifier = Modifier.fillMaxSize(), color = CatCrust) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(current = section, onSelect = { section = it })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    Section.BILLS -> BillsScreen(state)
                    Section.CALENDAR -> Placeholder("Calendar", "A full month grid with each day's bills shown in the cell, not as a dot.")
                    Section.INSIGHTS -> Placeholder("Insights", "Spending by category, a twelve month cash-flow projection, and a year-end summary you can print.")
                    Section.SETTINGS -> Placeholder("Settings", "Reminder timing, startup behaviour, the data folder, and import from BillMinder for Android.")
                }
            }
        }
    }
}

@Composable
private fun Sidebar(current: Section, onSelect: (Section) -> Unit) {
    Column(
        modifier = Modifier.width(216.dp).fillMaxHeight().background(CatMantle).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "BillMinder",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        )
        Text(
            "for PC",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 20.dp)
        )
        Section.entries.forEach { entry ->
            SidebarItem(entry, entry == current) { onSelect(entry) }
        }
    }
}

@Composable
private fun SidebarItem(section: Section, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        modifier = Modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(section.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(section.label, style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}

@Composable
private fun Placeholder(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(420.dp)
        )
    }
}
