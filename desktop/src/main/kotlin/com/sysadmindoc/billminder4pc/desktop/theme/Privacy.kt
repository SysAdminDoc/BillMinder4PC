package com.sysadmindoc.billminder4pc.desktop.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.sysadmindoc.billminder4pc.core.privacy.PrivacyText
import com.sysadmindoc.billminder4pc.desktop.Format

/**
 * Whether amounts should be blanked inside the window.
 *
 * A composition local rather than a parameter threaded through every screen, matching how the
 * phone app does it: masking has to be all or nothing, and one screen that forgets to pass the
 * flag is the whole feature failing.
 */
val LocalHideAmounts = compositionLocalOf { false }

/** A formatted amount, blanked when the window is masking. */
@Composable
@ReadOnlyComposable
fun privateAmount(amount: Double, currencyCode: String = "USD"): String =
    PrivacyText.inAppAmount(Format.money(amount, currencyCode), LocalHideAmounts.current)
