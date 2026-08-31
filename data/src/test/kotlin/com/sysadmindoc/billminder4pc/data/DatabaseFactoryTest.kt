package com.sysadmindoc.billminder4pc.data

import com.sysadmindoc.billminder4pc.core.model.Bill
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseFactoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `snapshot reopens with the live rows intact`() = runBlocking {
        val source = DatabaseFactory.openInMemory()
        val destination = temporaryFolder.root.toPath().resolve("snapshot.db")
        try {
            BillRepository(source).addBill(Bill(name = "Rent", amount = 1_450.0, dueDay = 1))
            DatabaseFactory.exportSnapshot(source, destination)
            assertTrue(destination.toFile().length() > 0L)

            val restored = DatabaseFactory.open(destination)
            try {
                val bill = restored.billDao().allBills().single()
                assertEquals("Rent", bill.name)
                assertEquals(1_450.0, bill.amount, 0.001)
            } finally {
                restored.close()
            }
        } finally {
            source.close()
        }
    }
}
