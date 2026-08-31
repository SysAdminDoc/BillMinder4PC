package com.sysadmindoc.billminder4pc.core.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.math.abs

@Entity(
    tableName = "bill_payees",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("billId")]
)
data class BillPayee(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val name: String,
    val sharePercent: Double
)

data class PayeeDraft(
    val name: String,
    val sharePercent: Double
)

object PayeeMath {
    fun totalPercent(payees: List<PayeeDraft>): Double = payees.sumOf { it.sharePercent }

    fun shareAmount(total: Double, sharePercent: Double): Double = total * sharePercent / 100.0

    fun isBalanced(payees: List<PayeeDraft>): Boolean =
        payees.isNotEmpty() && abs(totalPercent(payees) - 100.0) < 0.001
}
