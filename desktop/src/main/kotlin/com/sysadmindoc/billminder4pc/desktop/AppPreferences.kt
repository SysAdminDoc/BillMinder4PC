package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class AppPreferences(
    val reminderHour: Int = 9,
    val firstReminderDays: Int = 1,
    val dueDayReminder: Boolean = true,
    val overdueReminders: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val compactLayout: Boolean = true,
    val startAtLogin: Boolean = false,
    val keepRunningInTray: Boolean = true,
    val launchSection: String = "BILLS",
    val lastBackupAt: String = "Not yet"
)

/** Small properties-backed settings store. Tests use the in-memory form. */
class AppPreferencesStore(
    private val file: Path? = null,
    private val logger: AppLogger = AppLogger()
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (AppPreferences) -> AppPreferences): Result<AppPreferences> = runCatching {
        val next = transform(_state.value).normalized()
        file?.let { save(it, next) }
        _state.value = next
        next
    }.onFailure { logger.error("Saving app preferences failed", it) }

    private fun load(): AppPreferences {
        val source = file?.takeIf(Files::exists) ?: return AppPreferences()
        return runCatching {
            val properties = Properties()
            Files.newBufferedReader(source).use(properties::load)
            AppPreferences(
                reminderHour = properties.getProperty("reminderHour")?.toIntOrNull() ?: 9,
                firstReminderDays = properties.getProperty("firstReminderDays")?.toIntOrNull() ?: 1,
                dueDayReminder = properties.getProperty("dueDayReminder")?.toBooleanStrictOrNull() ?: true,
                overdueReminders = properties.getProperty("overdueReminders")?.toBooleanStrictOrNull() ?: true,
                themeMode = properties.getProperty("themeMode")
                    ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                    ?: ThemeMode.DARK,
                compactLayout = properties.getProperty("compactLayout")?.toBooleanStrictOrNull() ?: true,
                startAtLogin = properties.getProperty("startAtLogin")?.toBooleanStrictOrNull() ?: false,
                keepRunningInTray = properties.getProperty("keepRunningInTray")?.toBooleanStrictOrNull() ?: true,
                launchSection = properties.getProperty("launchSection") ?: "BILLS",
                lastBackupAt = properties.getProperty("lastBackupAt") ?: "Not yet"
            ).normalized()
        }.onFailure { logger.error("Loading app preferences failed", it) }
            .getOrDefault(AppPreferences())
    }

    private fun save(destination: Path, preferences: AppPreferences) {
        destination.parent?.let(Files::createDirectories)
        val temporary = destination.resolveSibling("${destination.fileName}.tmp")
        val properties = Properties().apply {
            setProperty("reminderHour", preferences.reminderHour.toString())
            setProperty("firstReminderDays", preferences.firstReminderDays.toString())
            setProperty("dueDayReminder", preferences.dueDayReminder.toString())
            setProperty("overdueReminders", preferences.overdueReminders.toString())
            setProperty("themeMode", preferences.themeMode.name)
            setProperty("compactLayout", preferences.compactLayout.toString())
            setProperty("startAtLogin", preferences.startAtLogin.toString())
            setProperty("keepRunningInTray", preferences.keepRunningInTray.toString())
            setProperty("launchSection", preferences.launchSection)
            setProperty("lastBackupAt", preferences.lastBackupAt)
        }
        Files.newBufferedWriter(temporary).use { properties.store(it, "BillMinder for PC settings") }
        runCatching {
            Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrElse {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun AppPreferences.normalized(): AppPreferences = copy(
        reminderHour = reminderHour.coerceIn(0, 23),
        firstReminderDays = firstReminderDays.coerceIn(0, 30),
        launchSection = launchSection.takeIf { value ->
            value in setOf("BILLS", "CALENDAR", "INSIGHTS", "SETTINGS")
        } ?: "BILLS"
    )
}
