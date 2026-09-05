package com.sysadmindoc.billminder4pc.desktop

import com.sysadmindoc.billminder4pc.core.model.Bill
import com.sysadmindoc.billminder4pc.core.privacy.PrivacyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * What leaves the window is held to a stricter standard than what is inside it. A tray alert sits
 * on the lock screen and in the notification history, so masked it names nothing at all, while an
 * in-window amount only loses its digits.
 */
class PrivacyMaskingTest {

    private val cycle: LocalDate = LocalDate.of(2026, 9, 15)

    private fun alert(autoPay: Boolean = false) = ReminderEvent(
        bill = Bill(
            id = 1,
            name = "Netflix",
            amount = 22.99,
            dueDay = cycle.dayOfMonth,
            isAutoPay = autoPay,
            anchorEpochDay = cycle.toEpochDay()
        ),
        cycleDate = cycle,
        kind = ReminderKind.PRIMARY,
        scheduledAt = Instant.parse("2026-09-15T09:00:00Z"),
        daysBeforeDue = 0
    ).toAlert()

    @Test
    fun `an unmasked notification names the bill and the amount`() {
        val title = alert().externalTitle(masked = false)
        assertTrue(title.startsWith("Netflix"))
        assertTrue(title.contains("22.99"))
        assertEquals("Due today", alert().externalBody(cycle, masked = false))
    }

    @Test
    fun `a masked notification names neither the bill nor the amount`() {
        val title = alert().externalTitle(masked = true)
        assertEquals(PrivacyText.HIDDEN_BILL_NAME, title)
        assertFalse("the bill name leaked", title.contains("Netflix"))
        assertFalse("the amount leaked", title.contains("22.99"))

        val body = alert().externalBody(cycle, masked = true)
        assertEquals(PrivacyText.HIDDEN_EXTERNAL_AMOUNT, body)
        assertFalse(body.contains("22.99"))
    }

    @Test
    fun `masking hides the auto-pay marker too, since it describes the bill`() {
        assertFalse(alert(autoPay = true).externalTitle(masked = true).contains("auto-pay"))
        assertTrue(alert(autoPay = true).externalTitle(masked = false).contains("auto-pay"))
    }

    @Test
    fun `an in-window amount keeps its shape rather than saying nothing`() {
        // The window is the user's own screen; the point is that a shoulder-surfer cannot read the
        // number, not that the row becomes unreadable.
        assertEquals(PrivacyText.HIDDEN_AMOUNT, PrivacyText.inAppAmount("$22.99", hidden = true))
        assertEquals("$22.99", PrivacyText.inAppAmount("$22.99", hidden = false))
    }

    @Test
    fun `the masking labels match the phone app string for string`() {
        // These are the strings BillMinder for Android uses. A screen share of either app should
        // look the same, and a shared backup carries the settings between them.
        assertEquals("••••", PrivacyText.HIDDEN_AMOUNT)
        assertEquals("Amount hidden", PrivacyText.HIDDEN_EXTERNAL_AMOUNT)
        assertEquals("Bill due", PrivacyText.HIDDEN_BILL_NAME)
    }

    @Test
    fun `both privacy settings survive a reload`() {
        val store = AppPreferencesStore()
        store.update { it.copy(hideAmounts = true, maskNotifications = true) }
        assertTrue(store.state.value.hideAmounts)
        assertTrue(store.state.value.maskNotifications)
    }

    @Test
    fun `privacy is off unless it is turned on`() {
        val fresh = AppPreferences()
        assertFalse(fresh.hideAmounts)
        assertFalse(fresh.maskNotifications)
    }
}
