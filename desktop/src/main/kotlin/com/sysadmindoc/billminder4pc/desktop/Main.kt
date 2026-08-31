package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.nio.file.Files
import javax.swing.SwingUtilities

const val APP_VERSION = "0.1.0"

fun main() {
    val instanceGuard = SingleInstanceGuard.acquire()
    if (!instanceGuard.isPrimary) {
        instanceGuard.close()
        return
    }

    // Opened before the composition starts, so the window never renders against a half-built
    // database and the process owns exactly one connection pool.
    val databaseExistedAtStartup = Files.exists(AppPaths.databaseFile)
    val db = DatabaseFactory.open()
    runBlocking { SampleData.seedIfFirstRun(db, databaseExistedAtStartup) }
    val state = AppState(db)

    println("BillMinder for PC $APP_VERSION, data directory ${AppPaths.dataDir}")

    application {
        var windowVisible by remember { mutableStateOf(true) }
        var activationSequence by remember { mutableStateOf(0L) }
        val windowState = rememberWindowState(size = DpSize(1120.dp, 760.dp))

        LaunchedEffect(instanceGuard) {
            instanceGuard.activationRequests.collect {
                windowVisible = true
                windowState.isMinimized = false
                activationSequence++
            }
        }
        // Compose issue 4231 requires this AWT foreground request outside the Window content.
        LaunchedEffect(activationSequence) {
            if (activationSequence > 0 && Desktop.isDesktopSupported()) {
                runCatching { Desktop.getDesktop().requestForeground(true) }
            }
        }

        Window(
            onCloseRequest = {
                state.close()
                exitApplication()
            },
            visible = windowVisible,
            title = "BillMinder for PC",
            state = windowState
        ) {
            LaunchedEffect(activationSequence) {
                if (activationSequence > 0) {
                    windowState.isMinimized = false
                    SwingUtilities.invokeLater {
                        window.toFront()
                        window.requestFocus()
                    }
                }
            }
            BillMinderTheme {
                App(state)
            }
        }
    }
    instanceGuard.close()
}
