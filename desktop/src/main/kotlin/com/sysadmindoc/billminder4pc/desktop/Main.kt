package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import javax.swing.SwingUtilities

const val APP_VERSION = "0.1.0"

fun main() {
    val logger = AppLogger()
    val previousExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
        logger.crash("Uncaught exception on thread ${thread.name}", failure)
        previousExceptionHandler?.uncaughtException(thread, failure) ?: failure.printStackTrace()
    }

    val guardResult = runCatching { SingleInstanceGuard.acquire() }
    guardResult.exceptionOrNull()?.let { logger.crash("Application startup failed", it) }
    val acquiredGuard = guardResult.getOrNull()
    if (acquiredGuard?.isPrimary == false) {
        acquiredGuard.close()
        return
    }

    val databaseResult = acquiredGuard?.let { runBlocking { openApplicationDatabase(logger) } }
    val startupFailure = guardResult.exceptionOrNull() ?: databaseResult?.exceptionOrNull()
    if (startupFailure != null) {
        try {
            application {
                var actionError by remember { mutableStateOf<String?>(null) }
                var activationSequence by remember { mutableStateOf(0L) }
                val recoveryWindowState = rememberWindowState(size = DpSize(760.dp, 520.dp))

                LaunchedEffect(acquiredGuard) {
                    acquiredGuard?.activationRequests?.collect {
                        recoveryWindowState.isMinimized = false
                        activationSequence++
                    }
                }
                LaunchedEffect(activationSequence) {
                    if (activationSequence > 0 && Desktop.isDesktopSupported()) {
                        runCatching { Desktop.getDesktop().requestForeground(true) }
                            .onFailure { logger.error("Recovery window activation failed", it) }
                    }
                }

                Window(
                    onCloseRequest = ::exitApplication,
                    title = "BillMinder for PC Recovery",
                    state = recoveryWindowState
                ) {
                    LaunchedEffect(activationSequence) {
                        if (activationSequence > 0) {
                            SwingUtilities.invokeLater {
                                window.toFront()
                                window.requestFocus()
                            }
                        }
                    }
                    BillMinderTheme {
                        StartupRecoveryScreen(
                            failureMessage = startupFailure.message ?: startupFailure.javaClass.simpleName,
                            dataDirectory = AppPaths.dataDir.toString(),
                            logFile = AppPaths.logFile.toString(),
                            actionError = actionError,
                            onOpenDataFolder = {
                                runCatching {
                                    check(Desktop.isDesktopSupported())
                                    Desktop.getDesktop().open(AppPaths.dataDir.toFile())
                                }.onFailure {
                                    logger.error("Opening the recovery data folder failed", it)
                                    actionError = "Couldn't open the data folder. Copy the Data path above."
                                }
                            },
                            onClose = ::exitApplication
                        )
                    }
                }
            }
        } finally {
            acquiredGuard?.close()
        }
        return
    }

    val instanceGuard = requireNotNull(acquiredGuard)
    val db = requireNotNull(databaseResult).getOrThrow()
    val state = AppState(db, logger = logger)

    logger.info("BillMinder for PC $APP_VERSION; data directory ${AppPaths.dataDir}")

    try {
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
                        .onFailure { logger.error("Foreground activation failed", it) }
                }
            }

            Window(
                onCloseRequest = ::exitApplication,
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
    } finally {
        state.close()
        instanceGuard.close()
    }
}
