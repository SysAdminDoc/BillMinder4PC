package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import com.sysadmindoc.billminder4pc.desktop.ui.ReminderPane
import com.sysadmindoc.billminder4pc.desktop.ui.Section
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the real window content offscreen. This is both the smoke test that the UI composes
 * against live database state and the source of the README screenshots, and it never opens a
 * window on the developer's display.
 */
class ScreenshotTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `the bills screen renders against a seeded database`() = runBlocking {
        val fixture = seededFixture("seed-state", Section.BILLS)
        assertTrue("sample data should have produced bills", fixture.dashboard.rows.isNotEmpty())

        val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
        outDir.mkdirs()

        try {
            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data"
            }
            val file = File(outDir, "bills.png")
            file.writeBytes(data.bytes)
            assertTrue("screenshot should not be empty", file.length() > 5_000)
            println("wrote ${file.absolutePath} (${file.length()} bytes)")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `the calendar screen renders against a seeded database`() = runBlocking {
        renderPage("calendar-state", Section.CALENDAR, "calendar.png")
    }

    @Test
    fun `the insights screen renders against a seeded database`() = runBlocking {
        renderPage("insights-state", Section.INSIGHTS, "insights.png")
    }

    @Test
    fun `the settings screen renders against a seeded database`() = runBlocking {
        renderPage("settings-state", Section.SETTINGS, "settings.png")
    }

    @Test
    fun `the bills screen renders in the light theme`() = runBlocking {
        val fixture = seededFixture("light-theme-state", Section.BILLS, ThemeMode.LIGHT)
        try {
            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG))
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "bills-light.png")
            file.writeBytes(data.bytes)
            assertTrue("light theme screenshot should not be empty", file.length() > 5_000)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `add bill action opens the entry form`() = runBlocking {
        val fixture = seededFixture("add-bill-state")
        try {
            fixture.scene.render().close()
            fixture.scene.click(1040f, 42f)
            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG))
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "add-bill.png")
            file.writeBytes(data.bytes)
            assertTrue("add bill form screenshot should not be empty", file.length() > 5_000)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `calendar agenda settles the selected bill`() = runBlocking {
        val fixture = seededFixture("calendar-pay-state", Section.CALENDAR)
        val spotify = fixture.dashboard.rows.single { it.bill.name == "Spotify" }
        try {
            fixture.scene.render().close()
            fixture.scene.click(1028f, 697f)
            val payment = withTimeout(5_000) {
                fixture.state.repository.observePayments().first { payments ->
                    payments.any { it.billId == spotify.bill.id }
                }.single { it.billId == spotify.bill.id }
            }
            assertEquals(spotify.bill.amount, payment.amount, 0.001)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `settings theme selection updates app preferences`() = runBlocking {
        val fixture = seededFixture("settings-theme-state", Section.SETTINGS)
        try {
            fixture.scene.render().close()
            fixture.scene.click(878f, 188f)
            val selected = withTimeout(5_000) {
                fixture.state.preferences.first { it.themeMode == ThemeMode.LIGHT }
            }
            assertEquals(ThemeMode.LIGHT, selected.themeMode)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `variable bill quick pay renders an amount prompt`() = runBlocking {
        val fixture = seededFixture("variable-seed-state")

        try {
            fixture.scene.render().close()
            val variableBillButton = Offset(1025f, 568f)
            fixture.scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = variableBillButton,
                button = PointerButton.Primary
            )
            fixture.scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = variableBillButton,
                button = PointerButton.Primary
            )

            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for the payment prompt"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "variable-payment.png")
            file.writeBytes(data.bytes)
            assertTrue("payment prompt screenshot should not be empty", file.length() > 5_000)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `fixed bill quick pay stays one click`() = runBlocking {
        val fixture = seededFixture("fixed-seed-state")
        val spotify = fixture.dashboard.rows.single { it.bill.name == "Spotify" }

        try {
            fixture.scene.render().close()
            val fixedBillButton = Offset(1025f, 438f)
            fixture.scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = fixedBillButton,
                button = PointerButton.Primary
            )
            fixture.scene.sendPointerEvent(
                eventType = PointerEventType.Release,
                position = fixedBillButton,
                button = PointerButton.Primary
            )

            val payment = withTimeout(5_000) {
                fixture.state.repository.observePayments().first { payments ->
                    payments.any { it.billId == spotify.bill.id }
                }.single { it.billId == spotify.bill.id }
            }
            assertEquals(spotify.bill.amount, payment.amount, 0.001)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `startup recovery screen renders without a database`() {
        val scene = ImageComposeScene(width = 760, height = 520, density = Density(1f)) {
            BillMinderTheme {
                StartupRecoveryScreen(
                    failureMessage = "File is not a database",
                    dataDirectory = "C:\\Users\\Example\\AppData\\Local\\BillMinder4PC",
                    logFile = "C:\\Users\\Example\\AppData\\Local\\BillMinder4PC\\billminder4pc.log",
                    onOpenDataFolder = {},
                    onClose = {}
                )
            }
        }
        try {
            val data = requireNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for the recovery screen"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "startup-recovery.png")
            file.writeBytes(data.bytes)
            assertTrue("recovery screenshot should not be empty", file.length() > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `the reminder pane renders an overdue bill with its actions`() {
        val today = LocalDate.of(2026, 9, 5)
        val alert = ReminderAlert(
            billId = 1,
            billName = "Rent",
            amount = 1_450.0,
            currency = "USD",
            isVariableAmount = false,
            isAutoPay = false,
            cycleDate = today.minusDays(2),
            cycleKey = today.minusDays(2).toString(),
            kind = ReminderKind.OVERDUE,
            scheduledAt = Instant.parse("2026-09-05T09:00:00Z")
        )
        val scene = ImageComposeScene(width = 520, height = 300, density = Density(1f)) {
            BillMinderTheme {
                ReminderPane(
                    alert = alert,
                    today = today,
                    billColor = 0xFF62A5FF,
                    remaining = 1,
                    onPay = {},
                    onSnooze = {},
                    onDismiss = {}
                )
            }
        }
        try {
            val data = requireNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for the reminder pane"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "reminder.png")
            file.writeBytes(data.bytes)
            assertTrue("reminder screenshot should not be empty", file.length() > 5_000)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `failed write renders an in-app error banner`() = runBlocking {
        val fixture = seededFixture("write-error-state")
        val spotify = fixture.dashboard.rows.single { it.bill.name == "Spotify" }
        try {
            fixture.database.close()
            fixture.state.markPaid(spotify)
            withTimeout(5_000) { fixture.state.errorMessage.first { it != null } }

            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for the write error"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "write-error.png")
            file.writeBytes(data.bytes)
            assertTrue("write error screenshot should not be empty", file.length() > 5_000)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `tray due-count badge renders offscreen`() {
        val scene = ImageComposeScene(width = 128, height = 128, density = Density(1f)) {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize().background(Color(0xFF020814)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = TrayBadgeIcon.painter(12),
                    contentDescription = "12 bills due",
                    modifier = androidx.compose.ui.Modifier.size(64.dp)
                )
            }
        }
        try {
            val data = requireNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for the tray badge"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, "tray-badge.png")
            file.writeBytes(data.bytes)
            assertTrue("tray badge screenshot should not be empty", file.length() > 1_000)
        } finally {
            scene.close()
        }
    }

    private suspend fun renderPage(folder: String, section: Section, fileName: String) {
        val fixture = seededFixture(folder, section)
        try {
            val data = requireNotNull(fixture.scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data for $fileName"
            }
            val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
            outDir.mkdirs()
            val file = File(outDir, fileName)
            file.writeBytes(data.bytes)
            assertTrue("$fileName should not be empty", file.length() > 5_000)
            println("wrote ${file.absolutePath} (${file.length()} bytes)")
        } finally {
            fixture.close()
        }
    }

    private fun ImageComposeScene.click(x: Float, y: Float) {
        sendPointerEvent(
            eventType = PointerEventType.Press,
            position = Offset(x, y),
            button = PointerButton.Primary
        )
        sendPointerEvent(
            eventType = PointerEventType.Release,
            position = Offset(x, y),
            button = PointerButton.Primary
        )
    }

    private suspend fun seededFixture(
        folder: String,
        section: Section = Section.BILLS,
        themeMode: ThemeMode = ThemeMode.DARK
    ): SceneFixture {
        val db = DatabaseFactory.openInMemory()
        val directory = temporaryFolder.newFolder(folder).toPath()
        val markerFile = directory.resolve("sample-data-initialized")
        val today = LocalDate.of(2026, 8, 31)
        SampleData.seedIfFirstRun(
            db,
            databaseExistedAtStartup = false,
            markerFile = markerFile,
            today = today
        )
        val zone = ZoneId.of("UTC")
        val preferencesStore = AppPreferencesStore().also { store ->
            store.update { it.copy(themeMode = themeMode) }.getOrThrow()
        }
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow(),
            preferencesStore = preferencesStore,
            logger = AppLogger(directory.resolve("app.log"), directory.resolve("crash.log"))
        )

        // collectAsState reads the StateFlow's current value, so the data has to land before the
        // first frame or the render captures the loading placeholder.
        val dashboard = withTimeout(15_000) { state.dashboard.first { it.loaded } }
        val scene = ImageComposeScene(width = 1120, height = 760, density = Density(1f)) {
            BillMinderTheme(themeMode = themeMode) { App(state, initialSection = section) }
        }
        return SceneFixture(state, dashboard, scene, db)
    }

    private data class SceneFixture(
        val state: AppState,
        val dashboard: Dashboard,
        val scene: ImageComposeScene,
        val database: BillDatabase
    ) : AutoCloseable {
        override fun close() {
            scene.close()
            state.close()
        }
    }
}
