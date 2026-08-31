package com.sysadmindoc.billminder4pc.desktop.theme

import androidx.compose.ui.graphics.Color

// Midnight ledger palette, carried over from BillMinder for Android so the two apps read as one
// product. Names stay stable because persisted bill category selections refer to them by index.
val CatCrust = Color(0xFF020814)
val CatMantle = Color(0xFF06101E)
val CatBase = Color(0xFF0A1525)
val CatSurfaceRaised = Color(0xFF0C1829)
val CatSurface0 = Color(0xFF122238)
val CatSurface1 = Color(0xFF263A55)
val CatSurface2 = Color(0xFF38506F)
val CatOverlay0 = Color(0xFF70809F)
val CatDivider = Color(0xFF22344C)
val CatText = Color(0xFFF4F7FF)
val CatSubtext0 = Color(0xFF98A7C5)
val CatSubtext1 = Color(0xFFBEC9DE)
val CatBlue = Color(0xFF62A5FF)
val CatSapphire = Color(0xFF5BC7FF)
val CatGreen = Color(0xFF75E5A5)
val CatYellow = Color(0xFFFFCB6B)
val CatPeach = Color(0xFFFF9369)
val CatRed = Color(0xFFFF7186)
val CatMauve = Color(0xFFC78CFF)
val CatPink = Color(0xFFF09BD8)
val CatTeal = Color(0xFF64DCC8)
val CatLavender = Color(0xFF91A7FF)
val CatFlamingo = Color(0xFFFFA7A7)
val CatRosewater = Color(0xFFFFC1B8)

val CategoryColors = listOf(
    CatBlue, CatYellow, CatGreen, CatPeach, CatMauve,
    CatPink, CatTeal, CatSapphire, CatFlamingo, CatLavender
)

/** Bills written by older versions stored an ARGB int; newer ones store a full long. */
fun storedBillColor(value: Long): Color =
    if (value ushr 32 == 0L) Color(value.toInt()) else Color(value)
