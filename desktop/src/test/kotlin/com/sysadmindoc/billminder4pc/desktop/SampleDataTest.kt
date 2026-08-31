package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.LocalDate

class SampleDataTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sample bills seed once and do not return after every bill is deleted`() = runBlocking {
        val dataDir = temporaryFolder.newFolder("data").toPath()
        val databaseFile = dataDir.resolve("billminder.db")
        val markerFile = dataDir.resolve("sample-data-initialized")
        val today = LocalDate.of(2026, 8, 31)

        val firstDatabase = DatabaseFactory.open(databaseFile)
        try {
            SampleData.seedIfFirstRun(
                db = firstDatabase,
                databaseExistedAtStartup = false,
                markerFile = markerFile,
                today = today
            )
            assertEquals(6, firstDatabase.billDao().allBills().size)
            assertTrue(Files.exists(markerFile))
            firstDatabase.billDao().deleteAllBills()
        } finally {
            firstDatabase.close()
        }

        val reopenedDatabase = DatabaseFactory.open(databaseFile)
        try {
            SampleData.seedIfFirstRun(
                db = reopenedDatabase,
                databaseExistedAtStartup = true,
                markerFile = markerFile,
                today = today.plusDays(1)
            )
            assertEquals(0, reopenedDatabase.billDao().allBills().size)
        } finally {
            reopenedDatabase.close()
        }
    }

    @Test
    fun `existing database without a marker is adopted without inserting samples`() = runBlocking {
        val dataDir = temporaryFolder.newFolder("upgrade").toPath()
        val markerFile = dataDir.resolve("sample-data-initialized")
        val database = DatabaseFactory.open(dataDir.resolve("billminder.db"))

        try {
            SampleData.seedIfFirstRun(
                db = database,
                databaseExistedAtStartup = true,
                markerFile = markerFile,
                today = LocalDate.of(2026, 8, 31)
            )
            assertEquals(0, database.billDao().allBills().size)
            assertTrue(Files.exists(markerFile))
        } finally {
            database.close()
        }
    }
}
