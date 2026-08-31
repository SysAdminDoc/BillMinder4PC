package com.sysadmindoc.billminder4pc.desktop

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SingleInstanceGuardTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `second acquisition activates the primary and exits`() = runBlocking {
        val directory = temporaryFolder.newFolder("instance").toPath()
        val lockFile = directory.resolve("app.lock")
        val endpointFile = directory.resolve("app.endpoint")
        val primary = SingleInstanceGuard.acquire(lockFile, endpointFile, waitMillis = 1_000)

        try {
            assertTrue(primary.isPrimary)
            val secondary = SingleInstanceGuard.acquire(lockFile, endpointFile, waitMillis = 1_000)
            try {
                assertFalse(secondary.isPrimary)
                withTimeout(2_000) { primary.activationRequests.first() }
            } finally {
                secondary.close()
            }
        } finally {
            primary.close()
        }

        assertFalse(Files.exists(endpointFile))
    }

    @Test
    fun `stale endpoint metadata is replaced when no process owns the lock`() {
        val directory = temporaryFolder.newFolder("stale").toPath()
        val lockFile = directory.resolve("app.lock")
        val endpointFile = directory.resolve("app.endpoint")
        Files.writeString(endpointFile, "invalid\nstale\n")

        val primary = SingleInstanceGuard.acquire(lockFile, endpointFile, waitMillis = 0)
        try {
            assertTrue(primary.isPrimary)
            val endpointLines = Files.readAllLines(endpointFile)
            assertTrue(endpointLines.first().toInt() in 1..65_535)
            assertTrue(endpointLines[1].isNotBlank())
        } finally {
            primary.close()
        }
    }
}
