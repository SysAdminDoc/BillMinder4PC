package com.sysadmindoc.billminder4pc.desktop

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

object Format {

    private val dayMonth = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val monthAbbrev = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    fun money(amount: Double, currencyCode: String = "USD"): String =
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
        }.format(amount)

    fun date(date: LocalDate): String = date.format(dayMonth)

    fun monthLabel(date: LocalDate): String = date.format(monthAbbrev).uppercase()

    /** Plain-language due state. The sign of the gap is what the reader actually needs first. */
    fun relativeDue(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        return when {
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            days == -1 -> "1 day overdue"
            days < 0 -> "${-days} days overdue"
            days < 7 -> "Due in $days days"
            else -> "Due ${date(date)}"
        }
    }
}
