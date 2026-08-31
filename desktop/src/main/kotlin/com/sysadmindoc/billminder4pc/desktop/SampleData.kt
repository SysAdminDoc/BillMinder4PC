package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.model.BillCategory
import com.sysadmindoc.billminder4pc.core.model.Recurrence
import com.sysadmindoc.billminder4pc.data.BillDatabase
import com.sysadmindoc.billminder4pc.data.BillRepository
import com.sysadmindoc.billminder4pc.desktop.theme.CatBlue
import com.sysadmindoc.billminder4pc.desktop.theme.CatGreen
import com.sysadmindoc.billminder4pc.desktop.theme.CatMauve
import com.sysadmindoc.billminder4pc.desktop.theme.CatPeach
import com.sysadmindoc.billminder4pc.desktop.theme.CatSapphire
import com.sysadmindoc.billminder4pc.desktop.theme.CatYellow
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate

/**
 * A first-run set of bills so a fresh install has something to look at. It only ever runs against
 * an empty database, so it cannot overwrite real data or reappear after the user deletes it.
 */
object SampleData {

    suspend fun seedIfEmpty(db: BillDatabase) {
        val dao = db.billDao()
        if (dao.allBills().isNotEmpty()) return

        val repo = BillRepository(db)
        val today = LocalDate.now()

        // Anchors are offsets from today rather than fixed days of the month, because a bill's
        // anchor is its first occurrence: anchoring everything earlier in the current month would
        // make every sample bill read as overdue on any day past the 25th.
        val netflix = bill("Netflix", 22.99, today.minusDays(4), BillCategory.SUBSCRIPTION, CatPeach.toArgb().toLong())
        val spotify = bill("Spotify", 12.99, today, BillCategory.SUBSCRIPTION, CatGreen.toArgb().toLong())
        val rent = bill("Rent", 1450.0, today.plusDays(2), BillCategory.RENT, CatBlue.toArgb().toLong(), autoPay = false)
        val electric = bill("Electric", 138.42, today.plusDays(6), BillCategory.UTILITIES, CatYellow.toArgb().toLong(), variable = true)
        val verizon = bill("Verizon", 89.00, today.plusDays(11), BillCategory.PHONE, CatSapphire.toArgb().toLong())
        val insurance = bill("Car insurance", 164.50, today.minusDays(2), BillCategory.INSURANCE, CatMauve.toArgb().toLong())

        listOf(netflix, spotify, rent, electric, verizon).forEach { repo.addBill(it) }

        // One already settled, so a fresh install shows what a paid bill looks like.
        val insuranceId = repo.addBill(insurance)
        repo.bill(insuranceId)?.let { repo.markPaid(it, today.minusDays(2)) }
    }

    private fun bill(
        name: String,
        amount: Double,
        anchor: LocalDate,
        category: BillCategory,
        color: Long,
        autoPay: Boolean = true,
        variable: Boolean = false
    ) = Bill(
        name = name,
        amount = amount,
        dueDay = anchor.dayOfMonth,
        category = category,
        recurrence = Recurrence.MONTHLY,
        isAutoPay = autoPay,
        color = color,
        isVariableAmount = variable,
        amountMin = if (variable) amount * 0.7 else null,
        amountMax = if (variable) amount * 1.35 else null,
        anchorEpochDay = anchor.toEpochDay()
    )
}
