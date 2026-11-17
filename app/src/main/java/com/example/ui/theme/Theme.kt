package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Gold400,
    onPrimary = Obsidian950,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = Gold400,
    secondary = Gold600,
    onSecondary = Obsidian950,
    secondaryContainer = Obsidian800,
    onSecondaryContainer = Ivory200,
    tertiary = EmeraldWin,
    onTertiary = Obsidian950,
    background = Obsidian950,
    onBackground = Ivory100,
    surface = Obsidian900,
    onSurface = Ivory100,
    surfaceVariant = Obsidian850,
    onSurfaceVariant = WarmSlate,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Obsidian850,
    surfaceContainerHigh = Obsidian800,
    surfaceContainerHighest = Obsidian700,
    outline = Obsidian700,
    outlineVariant = Color(0xFF2B2720),
    error = CrimsonCheck,
    onError = Ivory100
)

private val LightColorScheme = lightColorScheme(
    primary = GoldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E2B3),
    onPrimaryContainer = Color(0xFF3E2C00),
    secondary = Walnut700,
    onSecondary = Color.White,
    secondaryContainer = Parchment200,
    onSecondaryContainer = Walnut900,
    tertiary = EmeraldWin,
    onTertiary = Color.White,
    background = Parchment50,
    onBackground = Walnut900,
    surface = Parchment50,
    onSurface = Walnut900,
    surfaceVariant = Parchment100,
    onSurfaceVariant = Color(0xFF6B5B4D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Parchment100,
    surfaceContainer = Parchment100,
    surfaceContainerHigh = Parchment200,
    surfaceContainerHighest = Color(0xFFD8C9AE),
    outline = Color(0xFFCBBBA0),
    outlineVariant = Parchment200,
    error = CrimsonCheck,
    onError = Color.White
)

private val ChessShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ChessMasterTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChessTypography,
        shapes = ChessShapes,
        content = content
    )
}
