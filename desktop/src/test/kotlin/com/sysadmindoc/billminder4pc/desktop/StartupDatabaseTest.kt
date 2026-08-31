package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.LocalDate

class StartupDatabaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `healthy startup logs the bundled SQLite version`() = runBlocking {
        val directory = temporaryFolder.newFolder("healthy").toPath()
        val logger = AppLogger(directory.resolve("app.log"), directory.resolve("crash.log"))
        val result = openApplicationDatabase(
            logger = logger,
            databaseFile = directory.resolve("billminder.db"),
            sampleDataMarker = directory.resolve("sample-data-initialized"),
            today = LocalDate.of(2026, 8, 31)
        )

        val database = result.getOrThrow()
        try {
            assertEquals(6, database.billDao().allBills().size)
            assertTrue(
                Regex("SQLite version \\d+\\.\\d+")
                    .containsMatchIn(Files.readString(directory.resolve("app.log")))
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `corrupt database returns a failure and writes recovery logs`() = runBlocking {
        val directory = temporaryFolder.newFolder("corrupt").toPath()
        val databaseFile = directory.resolve("billminder.db")
        val logFile = directory.resolve("app.log")
        val crashFile = directory.resolve("crash.log")
        Files.writeString(databaseFile, "not a SQLite database")

        val result = openApplicationDatabase(
            logger = AppLogger(logFile, crashFile),
            databaseFile = databaseFile,
            sampleDataMarker = directory.resolve("sample-data-initialized"),
            today = LocalDate.of(2026, 8, 31)
        )

        assertTrue(result.isFailure)
        assertTrue(Files.readString(logFile).contains("Database startup failed"))
        assertTrue(Files.readString(crashFile).contains("Database startup failed"))
    }
}
