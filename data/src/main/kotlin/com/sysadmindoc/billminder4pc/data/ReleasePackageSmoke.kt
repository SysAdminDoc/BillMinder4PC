package com.sysadmindoc.billminder4pc.data

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/**
 * Opens the database through the optimized release classpath without starting the desktop UI.
 * The MSI task runs this entry point so reflective Room and native SQLite breakage cannot ship.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "Expected one directory for the release database smoke test." }

    val smokeRoot = Path.of(args.single())
    Files.createDirectories(smokeRoot)
    val databaseFile = Files.createTempDirectory(smokeRoot, "database-").resolve("smoke.db")
    val database = DatabaseFactory.open(databaseFile)

    try {
        runBlocking {
            check(database.billDao().allBills().isEmpty())
            check(DatabaseFactory.sqliteVersion(database).isNotBlank())
        }
    } finally {
        database.close()
    }
}
