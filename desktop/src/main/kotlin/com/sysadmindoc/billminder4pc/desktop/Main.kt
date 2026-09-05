package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.data.SnapshotStore
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import com.sysadmindoc.billminder4pc.desktop.ui.ReminderPane
import com.sysadmindoc.billminder4pc.desktop.ui.Section
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import javax.swing.SwingUtilities

const val APP_VERSION = "0.2.1"

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
    val snapshotStore = SnapshotStore(logger = logger)
    val state = AppState(
        db = db,
        preferencesStore = AppPreferencesStore(AppPaths.preferencesFile, logger),
        logger = logger,
        snapshotStore = snapshotStore
    )

    logger.info("BillMinder for PC $APP_VERSION; data directory ${AppPaths.dataDir}")

    try {
        application {
            var windowVisible by remember { mutableStateOf(true) }
            var activationSequence by remember { mutableStateOf(0L) }
            val windowState = rememberWindowState(size = DpSize(1120.dp, 760.dp))
            val dashboard by state.dashboard.collectAsState()
            val preferences by state.preferences.collectAsState()
            val trayPresentation = remember(dashboard) { dashboard.toTrayPresentation() }
            val trayIcon = remember(trayPresentation.dueCount) {
                TrayBadgeIcon.painter(trayPresentation.dueCount)
            }
            val trayState = rememberTrayState()
            val currentAlert by state.reminderAlerts.current.collectAsState()
            val pendingAlerts by state.reminderAlerts.pending.collectAsState()

            fun showWindow() {
                windowVisible = true
                windowState.isMinimized = false
                activationSequence++
            }

            LaunchedEffect(state) {
                state.alertBalloons.collect { alert ->
                    trayState.sendNotification(
                        Notification(
                            title = alert.externalTitle(preferences.maskNotifications),
                            message = alert.externalBody(
                                state.dashboard.value.asOfDate,
                                preferences.maskNotifications
                            ),
                            type = if (alert.kind == ReminderKind.OVERDUE) {
                                Notification.Type.Error
                            } else {
                                Notification.Type.Warning
                            }
                        )
                    )
                }
            }

            LaunchedEffect(instanceGuard) {
                instanceGuard.activationRequests.collect {
                    showWindow()
                }
            }

            // Restoring means putting a different file where the open database is, so the app has
            // to let go of it first and then stop. The next launch opens the restored data.
            LaunchedEffect(state) {
                state.restoreRequest.collect { file ->
                    if (file == null) return@collect
                    state.close()
                    db.close()
                    val outcome = snapshotStore.restore(file)
                    if (outcome.isSuccess) {
                        logger.info("Restored ${file.fileName}; exiting so the new file is opened cleanly")
                        instanceGuard.close()
                        exitApplication()
                    } else {
                        logger.error(
                            "Restore failed",
                            outcome.exceptionOrNull() ?: IllegalStateException("unknown")
                        )
                        state.cancelRestore()
                    }
                }
            }
            // Compose issue 4231 requires this AWT foreground request outside the Window content.
            LaunchedEffect(activationSequence) {
                if (activationSequence > 0 && Desktop.isDesktopSupported()) {
                    runCatching { Desktop.getDesktop().requestForeground(true) }
                        .onFailure { logger.error("Foreground activation failed", it) }
                }
            }

            if (isTraySupported && preferences.keepRunningInTray) {
                Tray(
                    icon = trayIcon,
                    state = trayState,
                    tooltip = trayPresentation.tooltip,
                    onAction = ::showWindow
                ) {
                    Item("Show BillMinder", onClick = ::showWindow)
                    Separator()
                    val nextBill = trayPresentation.nextBill
                    Item(
                        text = when {
                            nextBill == null -> "No unpaid bills"
                            nextBill.bill.isVariableAmount -> "Record ${nextBill.bill.name} payment..."
                            else -> "Mark ${nextBill.bill.name} paid"
                        },
                        enabled = nextBill != null,
                        onClick = {
                            if (nextBill != null &&
                                state.requestQuickPay(nextBill) == QuickPayResult.AMOUNT_REQUIRED
                            ) {
                                showWindow()
                            }
                        }
                    )
                    Separator()
                    Item("Exit", onClick = ::exitApplication)
                }
            }

            currentAlert?.let { alert ->
                val reminderWindowState = rememberWindowState(size = DpSize(520.dp, 300.dp))
                Window(
                    onCloseRequest = { state.dismissAlert(alert) },
                    title = "BillMinder reminder",
                    state = reminderWindowState,
                    alwaysOnTop = true,
                    resizable = false
                ) {
                    BillMinderTheme(themeMode = preferences.themeMode) {
                        ReminderPane(
                            alert = alert,
                            today = dashboard.asOfDate,
                            billColor = dashboard.rows
                                .firstOrNull { it.bill.id == alert.billId }
                                ?.bill
                                ?.color
                                ?: 0xFF89B4FA,
                            remaining = (pendingAlerts.size - 1).coerceAtLeast(0),
                            onPay = {
                                if (state.resolveAlert(alert) == QuickPayResult.AMOUNT_REQUIRED) {
                                    showWindow()
                                }
                            },
                            onSnooze = { choice -> state.snoozeAlert(alert, choice) },
                            onDismiss = { state.dismissAlert(alert) }
                        )
                    }
                }
            }

            Window(
                onCloseRequest = {
                    if (isTraySupported && preferences.keepRunningInTray) {
                        windowVisible = false
                    } else {
                        exitApplication()
                    }
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
                BillMinderTheme(themeMode = preferences.themeMode) {
                    App(
                        state = state,
                        initialSection = Section.entries.firstOrNull { it.name == preferences.launchSection }
                            ?: Section.BILLS
                    )
                }
            }
        }
    } finally {
        state.close()
        instanceGuard.close()
    }
}
