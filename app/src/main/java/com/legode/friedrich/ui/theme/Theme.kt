package com.legode.friedrich.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Палитра: чернильный фон и цвета немецкого флага — золото (акцент) и красный. */
object Fx {
    val Ink = Color(0xFF0B0D12)
    val Panel = Color(0xFF12151C)
    val Card = Color(0xFF181C26)
    val CardHigh = Color(0xFF202534)
    val Line = Color(0xFF2A3142)
    val Gold = Color(0xFFFFCE3A)
    val GoldDim = Color(0xFF3A3000)
    val Red = Color(0xFFE5484D)
    val Green = Color(0xFF6FD08C)
    val Blue = Color(0xFF7AB6FF)
    val Text = Color(0xFFECEEF3)
    val TextDim = Color(0xFF9BA3B4)
    val TextFaint = Color(0xFF6B7385)
    val OnGold = Color(0xFF1A1500)
}

private val Scheme = darkColorScheme(
    primary = Fx.Gold,
    onPrimary = Fx.OnGold,
    primaryContainer = Fx.GoldDim,
    onPrimaryContainer = Color(0xFFFFE38A),
    secondary = Fx.Red,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A1518),
    onSecondaryContainer = Color(0xFFFFB3B5),
    tertiary = Fx.Green,
    onTertiary = Color(0xFF00210E),
    background = Fx.Ink,
    onBackground = Fx.Text,
    surface = Fx.Panel,
    onSurface = Fx.Text,
    surfaceVariant = Fx.Card,
    onSurfaceVariant = Fx.TextDim,
    surfaceContainerLowest = Fx.Ink,
    surfaceContainerLow = Fx.Panel,
    surfaceContainer = Fx.Card,
    surfaceContainerHigh = Fx.CardHigh,
    surfaceContainerHighest = Color(0xFF272D3D),
    outline = Color(0xFF465066),
    outlineVariant = Fx.Line,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

private val Type = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
    )
}

@Composable
fun FriedrichTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * fontScale.coerceIn(0.8f, 1.5f))
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
    }
}
