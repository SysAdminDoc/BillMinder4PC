package com.sysadmindoc.billminder4pc.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.name

/** One rotated copy of the database, identified by the moment it was taken. */
data class Snapshot(val file: Path, val takenAt: Instant)

/**
 * Keeps a rotation of database snapshots and can put one back.
 *
 * The whole selling point of a single local SQLite file is that nothing can strand your history in
 * someone else's service. That is only true if the file is copied somewhere and the copy is known
 * to open, so every snapshot is verified by reopening it and reading through Room rather than by
 * checking that a file appeared.
 */
class SnapshotStore(
    private val databaseFile: Path = AppPaths.databaseFile,
    private val directory: Path = AppPaths.backupsDir,
    private val keep: Int = DEFAULT_KEEP,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zone),
    private val logger: AppLogger = AppLogger()
) {

    /** Snapshots on disk, newest first. Files that do not carry a readable stamp are ignored. */
    fun snapshots(): List<Snapshot> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.toList()
                .mapNotNull { path -> takenAt(path)?.let { Snapshot(path, it) } }
                .sortedByDescending { it.takenAt }
        }
    }

    /** Writes a snapshot only when the newest one is at least a day old, or there is none. */
    suspend fun snapshotIfDue(db: BillDatabase): Snapshot? {
        val newest = snapshots().firstOrNull()
        val now = clock.instant()
        if (newest != null && Duration.between(newest.takenAt, now) < Duration.ofDays(1)) return null
        return runCatching { snapshot(db) }
            .onFailure { logger.error("Rolling snapshot failed", it) }
            .getOrNull()
    }

    /**
     * Writes, verifies and prunes. A snapshot that cannot be reopened is deleted rather than
     * counted, so a rotation of unusable files can never masquerade as a backup.
     */
    suspend fun snapshot(db: BillDatabase): Snapshot {
        Files.createDirectories(directory)
        val takenAt = clock.instant()
        val destination = directory.resolve("$PREFIX${stampFormatter.format(LocalDateTime.ofInstant(takenAt, zone))}$SUFFIX")
        DatabaseFactory.exportSnapshot(db, destination)
        try {
            verify(destination)
        } catch (failure: Throwable) {
            Files.deleteIfExists(destination)
            throw IllegalStateException("Snapshot could not be reopened, so it was discarded", failure)
        }
        prune()
        logger.info("Database snapshot written to ${destination.name}")
        return Snapshot(destination, takenAt)
    }

    /**
     * Puts [snapshot] back, having first copied aside what is being replaced.
     *
     * **The database must be closed before calling this.** Windows will not let the live file be
     * overwritten while SQLite holds it open, and on platforms that would allow it the result is a
     * corrupt pair. Because the file is closed, copying it is already consistent. Returns the
     * safety copy so the caller can name it if the restored file turns out to be the wrong one.
     */
    suspend fun restore(snapshot: Path): Result<Path> = runCatching {
        require(Files.exists(snapshot)) { "That backup is no longer on disk" }
        verify(snapshot)

        Files.createDirectories(directory)
        val safety = directory.resolve("$REPLACED_PREFIX${stampFormatter.format(LocalDateTime.now(clock.withZone(zone)))}$SUFFIX")
        if (Files.exists(databaseFile)) {
            Files.copy(databaseFile, safety, StandardCopyOption.REPLACE_EXISTING)
        }

        Files.copy(snapshot, databaseFile, StandardCopyOption.REPLACE_EXISTING)
        // SQLite keeps recent commits in these until a clean close; leaving a previous database's
        // journal beside a restored file is how a restore silently un-restores itself.
        Files.deleteIfExists(Path.of("$databaseFile-wal"))
        Files.deleteIfExists(Path.of("$databaseFile-shm"))
        logger.info("Restored ${snapshot.name}; the replaced database is ${safety.name}")
        safety
    }.onFailure { logger.error("Restoring $snapshot failed", it) }

    /** Opens the file through Room and reads from it, so a corrupt copy fails here and not later. */
    private suspend fun verify(file: Path) {
        val db = DatabaseFactory.open(file)
        try {
            db.billDao().allBills()
        } finally {
            db.close()
        }
    }

    private fun prune() {
        snapshots().drop(keep).forEach { stale ->
            runCatching { Files.deleteIfExists(stale.file) }
                .onFailure { logger.error("Could not remove old snapshot ${stale.file.name}", it) }
        }
    }

    private fun takenAt(path: Path): Instant? {
        val name = path.name
        if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
        val stamp = name.removePrefix(PREFIX).removeSuffix(SUFFIX)
        return runCatching {
            LocalDateTime.parse(stamp, stampFormatter).atZone(zone).toInstant()
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_KEEP = 7
        private const val PREFIX = "snapshot-"
        private const val REPLACED_PREFIX = "replaced-"
        private const val SUFFIX = ".db"
        private val stampFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
