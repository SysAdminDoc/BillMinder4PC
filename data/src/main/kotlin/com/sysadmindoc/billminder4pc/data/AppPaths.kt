package com.sysadmindoc.billminder4pc.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where the app keeps its data on disk.
 *
 * `billminder4pc.dataDir` overrides everything, which is how tests get an isolated directory
 * instead of writing into the developer's real profile.
 */
object AppPaths {

    const val DATA_DIR_PROPERTY = "billminder4pc.dataDir"

    private const val APP_DIR = "BillMinder4PC"

    val dataDir: Path by lazy { resolveDataDir().also { Files.createDirectories(it) } }

    val databaseFile: Path get() = dataDir.resolve("billminder.db")

    val sampleDataMarker: Path get() = dataDir.resolve("sample-data-initialized")

    val preferencesFile: Path get() = dataDir.resolve("preferences.properties")

    val backupsDir: Path
        get() = dataDir.resolve("backups").also { Files.createDirectories(it) }

    val instanceLockFile: Path get() = dataDir.resolve("billminder4pc.lock")

    val instanceEndpointFile: Path get() = dataDir.resolve("billminder4pc.endpoint")

    val attachmentsDir: Path
        get() = dataDir.resolve("attachments").also { Files.createDirectories(it) }

    val logFile: Path get() = dataDir.resolve("billminder4pc.log")

    val crashLogFile: Path get() = dataDir.resolve("billminder4pc-crash.log")

    private fun resolveDataDir(): Path {
        System.getProperty(DATA_DIR_PROPERTY)?.takeIf { it.isNotBlank() }?.let {
            return Paths.get(it)
        }
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = Paths.get(System.getProperty("user.home"))
        return when {
            os.contains("win") ->
                System.getenv("LOCALAPPDATA")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Paths.get(it, APP_DIR) }
                    ?: home.resolve("AppData\\Local\\$APP_DIR")

            os.contains("mac") -> home.resolve("Library/Application Support/$APP_DIR")

            else ->
                System.getenv("XDG_DATA_HOME")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Paths.get(it, APP_DIR) }
                    ?: home.resolve(".local/share/$APP_DIR")
        }
    }
}
