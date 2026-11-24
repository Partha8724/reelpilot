package com.reelpilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** iOS-style system colours, light and dark. */
@Immutable
data class Palette(
    val bg: Color,          // grouped background
    val card: Color,        // grouped cell
    val elevated: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val fill: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val dark: Boolean,
) {
    val facebook = Color(0xFF1877F2)
    val instagram = Brush.linearGradient(listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5)))
    val hero = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFFFF2D78)))
}

private val Light = Palette(
    bg = Color(0xFFF2F2F7), card = Color.White, elevated = Color.White,
    label = Color(0xFF000000), secondary = Color(0x993C3C43), tertiary = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43), fill = Color(0x1F787880),
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500), purple = Color(0xFFAF52DE), dark = false,
)

private val Dark = Palette(
    bg = Color(0xFF000000), card = Color(0xFF1C1C1E), elevated = Color(0xFF2C2C2E),
    label = Color.White, secondary = Color(0x99EBEBF5), tertiary = Color(0x4DEBEBF5),
    separator = Color(0x99545458), fill = Color(0x3D787880),
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A), purple = Color(0xFFBF5AF2), dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }

object Pal {
    val c: Palette @Composable get() = LocalPalette.current
}

@Composable
fun ReelPilotTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) Dark else Light
    val scheme = if (dark) darkColorScheme(primary = p.blue, background = p.bg, surface = p.card, onSurface = p.label)
    else lightColorScheme(primary = p.blue, background = p.bg, surface = p.card, onSurface = p.label)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
