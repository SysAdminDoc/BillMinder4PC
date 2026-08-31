package com.sysadmindoc.billminder4pc.data

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AppLoggerTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `errors and crashes are written to durable files`() {
        val directory = temporaryFolder.newFolder("logs").toPath()
        val logFile = directory.resolve("app.log")
        val crashFile = directory.resolve("crash.log")
        val logger = AppLogger(
            logFile,
            crashFile,
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC)
        )

        logger.info("Application started")
        logger.error("Write failed", IllegalStateException("database closed"))
        logger.crash("Startup failed", IllegalArgumentException("corrupt database"))

        val log = Files.readString(logFile)
        val crash = Files.readString(crashFile)
        assertTrue(log.contains("2026-08-31T12:00:00Z [INFO] Application started"))
        assertTrue(log.contains("[ERROR] Write failed"))
        assertTrue(log.contains("database closed"))
        assertTrue(crash.contains("[CRASH] Startup failed"))
        assertTrue(crash.contains("corrupt database"))
    }
}
