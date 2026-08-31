package com.sysadmindoc.billminder4pc.data

import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.nio.file.Path

object DatabaseFactory {

    /**
     * Opens the on-disk database. BundledSQLiteDriver compiles SQLite from source into the app, so
     * the engine version does not depend on whatever the host happens to ship.
     */
    fun open(path: Path = AppPaths.databaseFile): BillDatabase =
        Room.databaseBuilder<BillDatabase>(name = path.toAbsolutePath().toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /** An empty database that never touches disk. Used by tests. */
    fun openInMemory(): BillDatabase =
        Room.inMemoryDatabaseBuilder<BillDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    suspend fun sqliteVersion(db: BillDatabase): String =
        db.useReaderConnection { connection ->
            connection.usePrepared("SELECT sqlite_version()") { statement ->
                check(statement.step()) { "SQLite returned no version row" }
                statement.getText(0)
            }
        }
}
