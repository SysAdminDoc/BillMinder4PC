package com.sysadmindoc.billminder4pc.data

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant

/** A small process logger that remains usable before Room or Compose has started. */
class AppLogger(
    private val logFile: Path = AppPaths.logFile,
    private val crashLogFile: Path = AppPaths.crashLogFile,
    private val clock: Clock = Clock.systemUTC()
) {
    private val writeLock = Any()

    fun info(message: String) {
        append(logFile, "INFO", message)
    }

    fun error(message: String, failure: Throwable) {
        append(logFile, "ERROR", message, failure)
    }

    fun crash(message: String, failure: Throwable) {
        append(logFile, "CRASH", message, failure)
        append(crashLogFile, "CRASH", message, failure)
    }

    private fun append(path: Path, level: String, message: String, failure: Throwable? = null) {
        val entry = buildString {
            append(Instant.now(clock))
            append(" [")
            append(level)
            append("] ")
            append(message)
            appendLine()
            failure?.let { appendLine(it.stackTraceToString()) }
        }

        try {
            synchronized(writeLock) {
                path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
                Files.writeString(
                    path,
                    entry,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.WRITE
                )
            }
        } catch (loggingFailure: Exception) {
            System.err.println("Could not write BillMinder log entry to $path")
            loggingFailure.printStackTrace()
        }
    }
}
