package com.sysadmindoc.billminder4pc.core.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class BillCategory(val label: String) {
    RENT("Rent/Mortgage"),
    UTILITIES("Utilities"),
    INSURANCE("Insurance"),
    PHONE("Phone/Internet"),
    SUBSCRIPTION("Subscription"),
    LOAN("Loan/Credit"),
    MEDICAL("Medical"),
    TRANSPORTATION("Transportation"),
    GROCERIES("Groceries"),
    EDUCATION("Education"),
    ENTERTAINMENT("Entertainment"),
    CHILDCARE("Childcare"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): BillCategory = entries.find { it.label == label } ?: OTHER
    }
}

enum class Recurrence(val label: String) {
    WEEKLY("Weekly"),
    BIWEEKLY("Bi-Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly"),
    ONE_TIME("One-Time")
}

enum class ReminderTiming(val label: String, val days: Int) {
    DAY_OF("Day of", 0),
    ONE_DAY("1 day before", 1),
    TWO_DAYS("2 days before", 2),
    THREE_DAYS("3 days before", 3),
    ONE_WEEK("1 week before", 7),
    TWO_WEEKS("2 weeks before", 14),
    ONE_MONTH("1 month before", 30)
}

enum class SortMode(val label: String) {
    DUE_DATE("Due date"),
    AMOUNT_ASC("Amount (low to high)"),
    AMOUNT_DESC("Amount (high to low)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    CATEGORY("Category")
}

/**
 * Column names and types here match BillMinder for Android schema version 7 exactly. The
 * interchange round-trip tests depend on that, so a field added on one side has to be added on
 * the other before an export written by either app can be read by both.
 */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Double,
    val dueDay: Int,
    val dueMonth: Int? = null,
    val dueYear: Int? = null,
    val category: BillCategory = BillCategory.OTHER,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val isAutoPay: Boolean = false,
    val notes: String = "",
    val reminderTiming: ReminderTiming = ReminderTiming.ONE_DAY,
    val secondReminderTiming: ReminderTiming? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val color: Long = 0xFF89B4FA,
    val paymentUrl: String = "",
    val tags: String = "",
    val isVariableAmount: Boolean = false,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
    val currency: String = "USD",
    /**
     * Epoch day of this bill's first occurrence. Every later occurrence is derived from it, so the
     * recurrence rule does not drift with the current date. Zero means "not yet normalized".
     */
    val anchorEpochDay: Long = 0L
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Bill::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["billId", "cycleKey"], unique = true)]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val billId: Long,
    val amount: Double,
    val paidAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    val note: String = "",
    val confirmationNumber: String = "",
    val attachmentName: String = "",
    val attachmentFile: String = "",
    val attachmentMime: String = "application/octet-stream",
    val currency: String = "USD",
    /** ISO local date of the occurrence this payment settles. Unique per bill. */
    val cycleKey: String = ""
)
