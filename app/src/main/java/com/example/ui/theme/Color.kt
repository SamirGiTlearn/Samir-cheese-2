package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Core Luxury Obsidian & Gold Palette
val Obsidian950 = Color(0xFF0C0B09)
val Obsidian900 = Color(0xFF14130F)
val Obsidian850 = Color(0xFF1C1A15)
val Obsidian800 = Color(0xFF25221C)
val Obsidian700 = Color(0xFF343028)

val Gold400 = Color(0xFFE5C158)
val Gold500 = Color(0xFFD4AF37)
val Gold600 = Color(0xFFB59024)
val GoldMuted = Color(0xFF8C7335)
val GoldContainerDark = Color(0xFF3A2F12)

val Ivory100 = Color(0xFFF6F1E7)
val Ivory200 = Color(0xFFE8DFCE)
val WarmSlate = Color(0xFFA79E8D)

// Light Theme Ivory & Walnut Palette
val Parchment50 = Color(0xFFFAF6EE)
val Parchment100 = Color(0xFFF2EADA)
val Parchment200 = Color(0xFFE4D8C1)
val Walnut900 = Color(0xFF231912)
val Walnut700 = Color(0xFF4F3B2B)
val GoldLightPrimary = Color(0xFF9E7714)

// Semantic Status Colors
val EmeraldWin = Color(0xFF38B27B)
val CrimsonCheck = Color(0xFFE04848)
val AmberWarning = Color(0xFFF0A830)

// Chessboard Theme Palettes
enum class BoardTheme(
    val displayName: String,
    val lightSquare: Color,
    val darkSquare: Color,
    val frameColor: Color,
    val coordinateColor: Color,
    val selectedHighlight: Color,
    val lastMoveHighlight: Color
) {
    ROYAL_WALNUT(
        displayName = "Royal Walnut",
        lightSquare = Color(0xFFEAD5B3),
        darkSquare = Color(0xFF8C5A38),
        frameColor = Color(0xFF241811),
        coordinateColor = Color(0xFFD4AF37),
        selectedHighlight = Color(0x99E5C158),
        lastMoveHighlight = Color(0x66D4AF37)
    ),
    CLASSIC_MAPLE(
        displayName = "Classic Tournament",
        lightSquare = Color(0xFFEEEED2),
        darkSquare = Color(0xFF769656),
        frameColor = Color(0xFF1E261A),
        coordinateColor = Color(0xFFDDE6C7),
        selectedHighlight = Color(0x99F6F669),
        lastMoveHighlight = Color(0x66BACA44)
    ),
    OBSIDIAN_GOLD(
        displayName = "Obsidian & Gold",
        lightSquare = Color(0xFFD7C8AA),
        darkSquare = Color(0xFF4B4337),
        frameColor = Color(0xFF14120F),
        coordinateColor = Color(0xFFE5C158),
        selectedHighlight = Color(0x99E5C158),
        lastMoveHighlight = Color(0x66B59024)
    )
}
