package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.APP_VERSION
import com.sysadmindoc.billminder4pc.desktop.AppPreferences
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.ThemeMode
import com.sysadmindoc.billminder4pc.data.AppPaths
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val reminderHours = listOf(7, 8, 9, 10, 12, 17, 18)
private val reminderDays = listOf(0, 1, 2, 3, 7, 14, 30)

@Composable
fun SettingsScreen(state: AppState) {
    val preferences by state.preferences.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageHeader(title = "Settings", subtitle = "Make reminders work your way") {
            ReminderStatus(preferences)
        }

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RemindersCard(state, preferences, Modifier.fillMaxWidth().weight(1.38f))
                AppBehaviorCard(state, preferences, Modifier.fillMaxWidth().weight(1f))
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppearanceCard(state, preferences, Modifier.fillMaxWidth().height(230.dp))
                DataPrivacyCard(state, preferences, Modifier.fillMaxWidth().weight(1f))
            }
        }

        LedgerCard(Modifier.fillMaxWidth().height(46.dp)) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BillMinder for PC", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Version $APP_VERSION", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReminderStatus(preferences: AppPreferences) {
    val success = ledgerSuccessColor()
    LedgerCard(Modifier.width(248.dp).height(52.dp), color = success.copy(alpha = 0.08f)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = success, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Reminders are active", style = MaterialTheme.typography.titleSmall, color = success)
                Text(
                    "Next check at ${formatHour(preferences.reminderHour)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RemindersCard(state: AppState, preferences: AppPreferences, modifier: Modifier) {
    SettingsCard("Reminders", modifier) {
        SettingRow(
            label = "Default reminder time",
            description = "Time to send daily reminders."
        ) {
            ValueControl(formatHour(preferences.reminderHour)) {
                val index = reminderHours.indexOf(preferences.reminderHour).coerceAtLeast(0)
                state.updatePreferences { it.copy(reminderHour = reminderHours[(index + 1) % reminderHours.size]) }
            }
        }
        SettingDivider()
        SettingRow(
            label = "First reminder",
            description = "Used when you add a new bill."
        ) {
            ValueControl(formatDays(preferences.firstReminderDays)) {
                val index = reminderDays.indexOf(preferences.firstReminderDays).coerceAtLeast(0)
                state.updatePreferences { it.copy(firstReminderDays = reminderDays[(index + 1) % reminderDays.size]) }
            }
        }
        SettingDivider()
        SettingRow("Due-day reminder", "Add a second reminder on the due date.") {
            SquareCheck(
                preferences.dueDayReminder,
                { value -> state.updatePreferences { it.copy(dueDayReminder = value) } },
                "Due-day reminder"
            )
        }
        SettingDivider()
        SettingRow("Overdue reminders", "Keep checking after a bill is due.") {
            SquareCheck(
                preferences.overdueReminders,
                { value -> state.updatePreferences { it.copy(overdueReminders = value) } },
                "Overdue reminders"
            )
        }
    }
}

@Composable
private fun AppBehaviorCard(state: AppState, preferences: AppPreferences, modifier: Modifier) {
    SettingsCard("App behavior", modifier) {
        SettingRow("Start when I sign in", "Available with the Windows reminder service.") {
            SquareCheck(
                checked = preferences.startAtLogin,
                onCheckedChange = {},
                contentDescription = "Start when I sign in",
                enabled = false
            )
        }
        SettingDivider()
        SettingRow("Keep running in tray", "Keep reminders active when the window closes.") {
            SquareCheck(
                preferences.keepRunningInTray,
                { value -> state.updatePreferences { it.copy(keepRunningInTray = value) } },
                "Keep running in tray"
            )
        }
        SettingDivider()
        SettingRow("Open on launch", "Choose the first page after startup.") {
            ValueControl(preferences.launchSection.lowercase().replaceFirstChar { it.uppercase() }) {
                val values = listOf("BILLS", "CALENDAR", "INSIGHTS", "SETTINGS")
                val index = values.indexOf(preferences.launchSection).coerceAtLeast(0)
                state.updatePreferences { it.copy(launchSection = values[(index + 1) % values.size]) }
            }
        }
    }
}

@Composable
private fun AppearanceCard(state: AppState, preferences: AppPreferences, modifier: Modifier) {
    SettingsCard("Appearance", modifier) {
        Text(
            "Choose your theme.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        ThemeSelector(preferences.themeMode) { mode ->
            state.updatePreferences { it.copy(themeMode = mode) }
        }
        Spacer(Modifier.height(10.dp))
        SettingDivider()
        SettingRow("Compact layout", "Show more content in less space.") {
            SquareCheck(
                preferences.compactLayout,
                { value -> state.updatePreferences { it.copy(compactLayout = value) } },
                "Compact layout"
            )
        }
    }
}

@Composable
private fun DataPrivacyCard(state: AppState, preferences: AppPreferences, modifier: Modifier) {
    SettingsCard("Data & privacy", modifier) {
        Text(
            "Your bills stay on this PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = LedgerControlShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "%LOCALAPPDATA%\\BillMinder4PC",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SquareIconButton(
                    Icons.Filled.ContentCopy,
                    "Copy data folder path",
                    onClick = {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            StringSelection(AppPaths.dataDir.toString()),
                            null
                        )
                    },
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                "Open data folder",
                onClick = state::openDataFolder,
                primary = false,
                icon = Icons.Filled.FolderOpen,
                modifier = Modifier.weight(1f)
            )
            ActionButton(
                "Export backup",
                onClick = state::exportBackup,
                primary = false,
                icon = Icons.Filled.SaveAlt,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        ActionButton(
            "Import Android backup · Planned",
            onClick = {},
            primary = false,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        SettingDivider()
        Text(
            "Last backup: ${preferences.lastBackupAt}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    LedgerCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(11.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String,
    control: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(53.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = control)
    }
}

@Composable
private fun SettingDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun ValueControl(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = LedgerControlShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.width(116.dp).height(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Surface(
                onClick = { onSelect(mode) },
                shape = when (mode) {
                    ThemeMode.DARK -> RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp)
                    ThemeMode.SYSTEM -> RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp)
                    ThemeMode.LIGHT -> RoundedCornerShape(0.dp)
                },
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(mode.label(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.DARK -> Icons.Filled.DarkMode
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.SYSTEM -> Icons.Filled.SettingsBrightness
}

private fun ThemeMode.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12:00 AM"
    hour < 12 -> "$hour:00 AM"
    hour == 12 -> "12:00 PM"
    else -> "${hour - 12}:00 PM"
}

private fun formatDays(days: Int): String = when (days) {
    0 -> "Day of"
    1 -> "1 day before"
    7 -> "1 week before"
    14 -> "2 weeks before"
    30 -> "1 month before"
    else -> "$days days before"
}
