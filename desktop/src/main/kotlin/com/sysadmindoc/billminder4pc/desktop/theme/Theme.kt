package com.sysadmindoc.billminder4pc.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sysadmindoc.billminder4pc.desktop.ThemeMode

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

private val DaylightLedger = lightColorScheme(
    primary = LedgerLightBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = LedgerLightText,
    secondary = Color(0xFF187A91),
    onSecondary = Color.White,
    tertiary = Color(0xFF6C4FA3),
    onTertiary = Color.White,
    background = LedgerLightBackground,
    onBackground = LedgerLightText,
    surface = LedgerLightSurface,
    onSurface = LedgerLightText,
    surfaceVariant = LedgerLightRaised,
    onSurfaceVariant = LedgerLightSubtext,
    surfaceContainerLowest = LedgerLightBackground,
    surfaceContainerLow = LedgerLightSidebar,
    surfaceContainer = LedgerLightSurface,
    surfaceContainerHigh = LedgerLightRaised,
    surfaceContainerHighest = Color(0xFFE2E9F3),
    outline = LedgerLightBorder,
    outlineVariant = Color(0xFFE4EAF2),
    error = LedgerLightRed,
    onError = Color.White
)

private val systemUsesDarkTheme: Boolean by lazy {
    if (!System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) {
        false
    } else {
        runCatching {
            val process = ProcessBuilder(
                "reg.exe",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            process.exitValue() == 0 && Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x0", RegexOption.IGNORE_CASE)
                .containsMatchIn(output)
        }.getOrDefault(true)
    }
}

@Composable
fun BillMinderTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        ThemeMode.DARK -> MidnightLedger
        ThemeMode.LIGHT -> DaylightLedger
        ThemeMode.SYSTEM -> if (systemUsesDarkTheme) MidnightLedger else DaylightLedger
    }
    MaterialTheme(
        colorScheme = colors,
        typography = BillMinderTypography,
        content = content
    )
}
