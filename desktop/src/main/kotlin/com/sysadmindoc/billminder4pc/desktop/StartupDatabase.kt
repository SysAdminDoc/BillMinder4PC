package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.data.AppLogger
import com.sysadmindoc.billminder4pc.data.AppPaths
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.DatabaseFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

internal suspend fun openApplicationDatabase(
    logger: AppLogger,
    databaseFile: Path = AppPaths.databaseFile,
    sampleDataMarker: Path = AppPaths.sampleDataMarker,
    today: LocalDate = LocalDate.now()
): Result<BillDatabase> {
    var database: BillDatabase? = null
    return try {
        val existedAtStartup = Files.exists(databaseFile)
        val openedDatabase = DatabaseFactory.open(databaseFile)
        database = openedDatabase
        SampleData.seedIfFirstRun(openedDatabase, existedAtStartup, sampleDataMarker, today)
        logger.info("SQLite version ${DatabaseFactory.sqliteVersion(openedDatabase)}")
        Result.success(openedDatabase)
    } catch (failure: Throwable) {
        runCatching { database?.close() }
        logger.crash("Database startup failed for $databaseFile", failure)
        Result.failure(failure)
    }
}
