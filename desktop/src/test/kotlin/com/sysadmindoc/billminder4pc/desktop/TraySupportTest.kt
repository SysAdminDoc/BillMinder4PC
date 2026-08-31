package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.graphics.toPixelMap
import com.sysadmindoc.billminder4pc.core.cycle.ResolvedCycle
import com.sysadmindoc.billminder4pc.core.model.Bill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class TraySupportTest {

    @Test
    fun `tray presentation counts actionable bills and lists every bill due today`() {
        val today = LocalDate.of(2026, 8, 31)
        val overdue = row(1, "Rent", today.minusDays(1), isOverdue = true)
        val dueToday = row(2, "Electric", today)
        val paidToday = row(3, "Water", today, isPaid = true)
        val upcoming = row(4, "Phone", today.plusDays(2))

        val presentation = Dashboard(
            rows = listOf(overdue, dueToday, paidToday, upcoming),
            asOfDate = today,
            loaded = true
        ).toTrayPresentation()

        assertEquals(2, presentation.dueCount)
        assertEquals("BillMinder for PC\nToday: Electric, Water (paid)", presentation.tooltip)
        assertEquals(overdue, presentation.nextBill)
    }

    @Test
    fun `tray badge pixels change with the due count`() {
        val emptyBadge = TrayBadgeIcon.image(0).toPixelMap()
        val dueBadge = TrayBadgeIcon.image(12).toPixelMap()

        assertEquals(64, emptyBadge.width)
        assertEquals(64, emptyBadge.height)
        assertNotEquals(emptyBadge.buffer.contentHashCode(), dueBadge.buffer.contentHashCode())
        assertNotEquals(emptyBadge[49, 15], dueBadge[49, 15])
    }

    private fun row(
        id: Long,
        name: String,
        dueDate: LocalDate,
        isPaid: Boolean = false,
        isOverdue: Boolean = false
    ): BillRow = BillRow(
        bill = Bill(
            id = id,
            name = name,
            amount = 100.0,
            dueDay = dueDate.dayOfMonth,
            anchorEpochDay = dueDate.toEpochDay()
        ),
        cycle = ResolvedCycle(
            billId = id,
            date = dueDate,
            cycleKey = dueDate.toString(),
            dueAt = 0L,
            daysUntilDue = 0,
            isPaid = isPaid,
            isOverdue = isOverdue,
            payment = null
        )
    )
}
