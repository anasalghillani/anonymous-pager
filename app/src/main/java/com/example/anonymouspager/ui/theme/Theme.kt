package com.example.anonymouspager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palette taken from the Wen logo (concept A): dark pager shell, green LCD, orange status light. */
object WenColors {
    val Night = Color(0xFF12141C)
    val Surface = Color(0xFF1A1D27)
    val SurfaceHigh = Color(0xFF232733)
    val Shell = Color(0xFF2A2E3A)
    val Outline = Color(0xFF3B4152)
    val OutlineSoft = Color(0xFF2C3140)

    val Lcd = Color(0xFFB6E36B)
    val LcdInk = Color(0xFF1E2A12)
    val LcdSoft = Color(0x1FB6E36B)

    val Ember = Color(0xFFFF7A45)
    val EmberSoft = Color(0x26FF7A45)
    val Danger = Color(0xFFFF5D5D)
    val DangerSoft = Color(0x26FF5D5D)

    val Text = Color(0xFFEEF0F5)
    val TextMuted = Color(0xFF9AA1B2)
    val TextFaint = Color(0xFF6B7285)
}

/** 4dp spacing scale, used everywhere instead of ad-hoc paddings. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

object WenType {
    /** Codes, tags and LCD readouts: the only place the monospace "pager" font is used. */
    val Code = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 6.sp
    )
    val Readout = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    )
    val Tag = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 1.sp
    )
}

private val WenColorScheme = darkColorScheme(
    primary = WenColors.Lcd,
    onPrimary = WenColors.LcdInk,
    primaryContainer = WenColors.LcdSoft,
    onPrimaryContainer = WenColors.Lcd,
    secondary = WenColors.Ember,
    onSecondary = WenColors.Night,
    secondaryContainer = WenColors.EmberSoft,
    onSecondaryContainer = WenColors.Ember,
    background = WenColors.Night,
    onBackground = WenColors.Text,
    surface = WenColors.Surface,
    onSurface = WenColors.Text,
    surfaceVariant = WenColors.SurfaceHigh,
    onSurfaceVariant = WenColors.TextMuted,
    surfaceContainerLowest = WenColors.Night,
    surfaceContainerLow = WenColors.Surface,
    surfaceContainer = WenColors.Surface,
    surfaceContainerHigh = WenColors.SurfaceHigh,
    surfaceContainerHighest = WenColors.Shell,
    outline = WenColors.Outline,
    outlineVariant = WenColors.OutlineSoft,
    error = WenColors.Danger,
    onError = WenColors.Night
)

// System font (Roboto + Noto Naskh Arabic) reads well in both languages; weights do the hierarchy.
private val WenTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    )
}

private val WenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WenColorScheme,
        typography = WenTypography,
        shapes = WenShapes,
        content = content
    )
}
