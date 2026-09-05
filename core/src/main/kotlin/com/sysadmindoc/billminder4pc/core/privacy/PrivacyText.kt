package com.sysadmindoc.billminder4pc.core.privacy

/**
 * Masking labels shared with BillMinder for Android, string for string.
 *
 * Ported unchanged from that app's `security/PrivacyText.kt`. The two apps mask the same things
 * the same way, so a screen share or a screenshot of either looks the same. In-app masking keeps
 * the shape of an amount; anything that leaves the window says nothing at all, because a
 * notification sits on the lock screen and in the Action Center history.
 */
object PrivacyText {
    const val HIDDEN_AMOUNT = "••••"
    const val HIDDEN_EXTERNAL_AMOUNT = "Amount hidden"
    const val HIDDEN_BILL_NAME = "Bill due"

    fun inAppAmount(formattedAmount: String, hidden: Boolean): String =
        if (hidden) HIDDEN_AMOUNT else formattedAmount

    fun externalAmount(formattedAmount: String, hidden: Boolean): String =
        if (hidden) HIDDEN_EXTERNAL_AMOUNT else formattedAmount

    fun externalBillName(billName: String, hidden: Boolean): String =
        if (hidden) HIDDEN_BILL_NAME else billName
}
