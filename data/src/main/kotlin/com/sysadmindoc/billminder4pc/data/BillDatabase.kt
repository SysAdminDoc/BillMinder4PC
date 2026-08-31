package com.sysadmindoc.billminder4pc.data

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.BillPayee
import com.sysadmindoc.billminder4pc.core.model.Payment

/**
 * Version 1 is a fresh desktop database. The table shapes deliberately match BillMinder for
 * Android schema version 7, so an export from the phone maps across field for field, but the two
 * version numbers are independent and are expected to diverge.
 *
 * There is deliberately no `@TypeConverters` here. Room already stores an enum by its name, and
 * declaring converters for the same enums makes the processor fail with a bare "references a type
 * that is not present" that names nothing.
 */
@Database(
    entities = [Bill::class, Payment::class, BillPayee::class],
    version = 1,
    exportSchema = true
)
abstract class BillDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
}
