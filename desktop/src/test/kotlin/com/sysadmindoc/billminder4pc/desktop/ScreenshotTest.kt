package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Renders the real window content offscreen. This is both the smoke test that the UI composes
 * against live database state and the source of the README screenshots, and it never opens a
 * window on the developer's display.
 */
class ScreenshotTest {

    @Test
    fun `the bills screen renders against a seeded database`() = runBlocking {
        val db = DatabaseFactory.openInMemory()
        SampleData.seedIfEmpty(db)
        val state = AppState(db)

        // collectAsState reads the StateFlow's current value, so the data has to have landed
        // before the first frame or the render captures the loading placeholder.
        val dashboard = withTimeout(15_000) { state.dashboard.first { it.loaded } }
        assertTrue("sample data should have produced bills", dashboard.rows.isNotEmpty())

        val outDir = File(System.getProperty("billminder4pc.screenshotDir") ?: "build/screenshots")
        outDir.mkdirs()

        val scene = ImageComposeScene(width = 1120, height = 760, density = Density(1f)) {
            BillMinderTheme { App(state) }
        }
        try {
            val data = requireNotNull(scene.render().encodeToData(EncodedImageFormat.PNG)) {
                "Skia returned no PNG data"
            }
            val file = File(outDir, "bills.png")
            file.writeBytes(data.bytes)
            assertTrue("screenshot should not be empty", file.length() > 5_000)
            println("wrote ${file.absolutePath} (${file.length()} bytes)")
        } finally {
            scene.close()
            state.close()
        }
    }
}
