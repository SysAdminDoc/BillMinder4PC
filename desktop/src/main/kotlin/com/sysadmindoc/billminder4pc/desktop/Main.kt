package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import com.sysadmindoc.billminder4pc.desktop.theme.BillMinderTheme
import com.sysadmindoc.billminder4pc.desktop.ui.App
import kotlinx.coroutines.runBlocking

const val APP_VERSION = "0.1.0"

fun main() {
    // Opened before the composition starts, so the window never renders against a half-built
    // database and the process owns exactly one connection pool.
    val db = DatabaseFactory.open()
    runBlocking { SampleData.seedIfEmpty(db) }
    val state = AppState(db)

    println("BillMinder for PC $APP_VERSION, data directory ${AppPaths.dataDir}")

    application {
        Window(
            onCloseRequest = {
                state.close()
                exitApplication()
            },
            title = "BillMinder for PC",
            state = rememberWindowState(size = DpSize(1120.dp, 760.dp))
        ) {
            BillMinderTheme {
                App(state)
            }
        }
    }
}
