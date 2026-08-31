package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
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
        val fixture = seededFixture("seed-state")
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
    fun `variable bill quick pay renders an amount prompt`() = runBlocking {
        val fixture = seededFixture("variable-seed-state")

        try {
            fixture.scene.render().close()
            val variableBillButton = Offset(1052f, 555f)
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
            val fixedBillButton = Offset(1052f, 387f)
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

    private suspend fun seededFixture(folder: String): SceneFixture {
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
        val state = AppState(
            db = db,
            zone = zone,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), zone),
            dayChangeSignals = emptyFlow(),
            logger = AppLogger(directory.resolve("app.log"), directory.resolve("crash.log"))
        )

        // collectAsState reads the StateFlow's current value, so the data has to land before the
        // first frame or the render captures the loading placeholder.
        val dashboard = withTimeout(15_000) { state.dashboard.first { it.loaded } }
        val scene = ImageComposeScene(width = 1120, height = 760, density = Density(1f)) {
            BillMinderTheme { App(state) }
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
