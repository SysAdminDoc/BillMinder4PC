package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `preferences survive a store reload`() {
        val directory = temporaryFolder.newFolder("preferences").toPath()
        val file = directory.resolve("preferences.properties")
        val logger = AppLogger(directory.resolve("app.log"), directory.resolve("crash.log"))
        val store = AppPreferencesStore(file, logger)

        store.update {
            it.copy(
                reminderHour = 17,
                overdueReminders = false,
                themeMode = ThemeMode.LIGHT,
                compactLayout = false,
                launchSection = "CALENDAR"
            )
        }.getOrThrow()

        val reloaded = AppPreferencesStore(file, logger).state.value
        assertEquals(17, reloaded.reminderHour)
        assertEquals(false, reloaded.overdueReminders)
        assertEquals(ThemeMode.LIGHT, reloaded.themeMode)
        assertEquals(false, reloaded.compactLayout)
        assertEquals("CALENDAR", reloaded.launchSection)
    }
}
