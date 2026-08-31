package com.sysadmindoc.billminder4pc.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MidnightLedger = darkColorScheme(
    primary = CatBlue,
    onPrimary = CatCrust,
    primaryContainer = CatSurface0,
    onPrimaryContainer = CatText,
    secondary = CatSapphire,
    onSecondary = CatCrust,
    tertiary = CatMauve,
    onTertiary = CatCrust,
    background = CatCrust,
    onBackground = CatText,
    surface = CatBase,
    onSurface = CatText,
    surfaceVariant = CatSurfaceRaised,
    onSurfaceVariant = CatSubtext1,
    surfaceContainerLowest = CatCrust,
    surfaceContainerLow = CatMantle,
    surfaceContainer = CatSurfaceRaised,
    surfaceContainerHigh = CatSurface0,
    surfaceContainerHighest = CatSurface1,
    outline = CatDivider,
    outlineVariant = CatSurface0,
    error = CatRed,
    onError = CatCrust
)

@Composable
fun BillMinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightLedger,
        typography = BillMinderTypography,
        content = content
    )
}
