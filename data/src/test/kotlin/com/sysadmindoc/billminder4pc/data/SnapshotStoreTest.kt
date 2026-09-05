package com.sysadmindoc.billminder4pc.data

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single-file design only protects anyone if the file is copied and the copy is known to open.
 */
class SnapshotStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val zone = ZoneId.of("UTC")

    private class MovableClock(var currentInstant: Instant, private val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MovableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
    }

    private fun bill(name: String) = Bill(
        name = name,
        amount = 100.0,
        dueDay = 5,
        recurrence = Recurrence.MONTHLY,
        anchorEpochDay = LocalDate.of(2026, 9, 5).toEpochDay()
    )

    private class Fixture(
        val databaseFile: Path,
        val directory: Path,
        val db: BillDatabase,
        val clock: MovableClock
    )

    private fun fixture(keep: Int = 3, start: String = "2026-09-05T09:00:00Z"): Fixture {
        val root = temporaryFolder.newFolder("snapshots-${System.nanoTime()}").toPath()
        val databaseFile = root.resolve("billminder.db")
        val db = DatabaseFactory.open(databaseFile)
        return Fixture(databaseFile, root.resolve("backups"), db, MovableClock(Instant.parse(start), zone))
    }

    private fun store(f: Fixture, keep: Int = 3) = SnapshotStore(
        databaseFile = f.databaseFile,
        directory = f.directory,
        keep = keep,
        zone = zone,
        clock = f.clock,
        logger = AppLogger(
            temporaryFolder.newFile("app-${System.nanoTime()}.log").toPath(),
            temporaryFolder.newFile("crash-${System.nanoTime()}.log").toPath()
        )
    )

    @Test
    fun `a snapshot captures the rows and can be read back`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            val snapshot = s.snapshot(f.db)

            assertTrue(Files.exists(snapshot.file))
            val reopened = DatabaseFactory.open(snapshot.file)
            try {
                assertEquals(listOf("Rent"), reopened.billDao().allBills().map { it.name })
            } finally {
                reopened.close()
            }
        } finally {
            f.db.close()
        }
    }

    @Test
    fun `rotation keeps only the newest N`() = runBlocking {
        val f = fixture()
        val s = store(f, keep = 3)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            repeat(5) { index ->
                f.clock.currentInstant = Instant.parse("2026-09-05T09:00:00Z").plusSeconds(index * 3600L)
                s.snapshot(f.db)
            }
            val kept = s.snapshots()
            assertEquals(3, kept.size)
            // Newest first, and the two oldest are gone.
            assertEquals(
                listOf(
                    Instant.parse("2026-09-05T13:00:00Z"),
                    Instant.parse("2026-09-05T12:00:00Z"),
                    Instant.parse("2026-09-05T11:00:00Z")
                ),
                kept.map { it.takenAt }
            )
        } finally {
            f.db.close()
        }
    }

    @Test
    fun `the daily rotation writes once a day, not once a launch`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            assertNotNull(s.snapshotIfDue(f.db))

            f.clock.currentInstant = Instant.parse("2026-09-05T20:00:00Z")
            assertNull("a second launch the same day must not snapshot again", s.snapshotIfDue(f.db))

            f.clock.currentInstant = Instant.parse("2026-09-06T10:00:00Z")
            assertNotNull("a day later it should snapshot", s.snapshotIfDue(f.db))
            assertEquals(2, s.snapshots().size)
        } finally {
            f.db.close()
        }
    }

    @Test
    fun `a snapshot that cannot be reopened is discarded rather than counted`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            val good = s.snapshot(f.db)
            // Corrupt it the way a truncated copy would be, then prove verification rejects it.
            Files.write(good.file, byteArrayOf(1, 2, 3, 4))

            val restored = s.restore(good.file)
            assertTrue("a corrupt backup must not be restorable", restored.isFailure)
        } finally {
            f.db.close()
        }
    }

    @Test
    fun `restoring puts the old rows back and keeps a copy of what it replaced`() = runBlocking {
        val f = fixture()
        val s = store(f)
        val repository = BillRepository(f.db)
        try {
            repository.addBill(bill("Rent"))
            val snapshot = s.snapshot(f.db)

            // The user then adds something they later want to undo.
            repository.addBill(bill("Mistake"))
            assertEquals(2, f.db.billDao().allBills().size)

            f.clock.currentInstant = Instant.parse("2026-09-05T18:00:00Z")
            // Windows will not let the live file be overwritten while SQLite holds it open.
            f.db.close()
            val safety = s.restore(snapshot.file)
            assertTrue("restore failed: ${safety.exceptionOrNull()?.message}", safety.isSuccess)

            val reopened = DatabaseFactory.open(f.databaseFile)
            try {
                assertEquals(listOf("Rent"), reopened.billDao().allBills().map { it.name })
            } finally {
                reopened.close()
            }

            // The replaced database is still available, with the row the restore removed.
            val replaced = DatabaseFactory.open(safety.getOrThrow())
            try {
                assertEquals(
                    listOf("Mistake", "Rent"),
                    replaced.billDao().allBills().map { it.name }.sorted()
                )
            } finally {
                replaced.close()
            }
        } finally {
            runCatching { f.db.close() }
        }
    }

    @Test
    fun `restoring clears the previous database's journal files`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            val snapshot = s.snapshot(f.db)
            f.db.close()

            // A stale journal beside the restored file can replay the old database back over it.
            val wal = Path.of("${f.databaseFile}-wal")
            Files.write(wal, byteArrayOf(9, 9, 9))

            assertTrue(s.restore(snapshot.file).isSuccess)
            assertFalse("a stale -wal must not survive a restore", Files.exists(wal))
        } finally {
            runCatching { f.db.close() }
        }
    }

    @Test
    fun `unrelated files in the backup folder are ignored`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            s.snapshot(f.db)
            Files.write(f.directory.resolve("notes.txt"), "hello".toByteArray())
            Files.write(f.directory.resolve("snapshot-not-a-date.db"), byteArrayOf(1))

            assertEquals(1, s.snapshots().size)
        } finally {
            f.db.close()
        }
    }

    @Test
    fun `restoring a file that is gone fails instead of emptying the database`() = runBlocking {
        val f = fixture()
        val s = store(f)
        try {
            BillRepository(f.db).addBill(bill("Rent"))
            val missing = f.directory.resolve("snapshot-20260101-000000.db")

            assertTrue(s.restore(missing).isFailure)
            assertEquals("the live database must be untouched", 1, f.db.billDao().allBills().size)
        } finally {
            f.db.close()
        }
    }
}
