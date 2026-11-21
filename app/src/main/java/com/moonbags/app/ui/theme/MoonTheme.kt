package com.moonbags.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0C1016)
val Paper = Color(0xFFEFE8DC)
val Brass = Color(0xFFD4A24A)
val BrassInk = Color(0xFF3A2A10)
val Panel = Color(0xFF1B2430)
val Up = Color(0xFF3DDC84)
val Down = Color(0xFFFF4D4D)

private val InkScheme = darkColorScheme(
    primary = Brass,
    onPrimary = BrassInk,
    background = Ink,
    onBackground = Paper,
    surface = Panel,
    onSurface = Paper,
    surfaceVariant = Color(0xFF141B24),
    onSurfaceVariant = Color(0xFF93A0B0),
    error = Down,
    outline = Color(0xFF314054),
)

private val PaperScheme = lightColorScheme(
    primary = Color(0xFF8D6420),
    onPrimary = Paper,
    background = Paper,
    onBackground = Color(0xFF1A140E),
    surface = Color(0xFFF6F0E4),
    onSurface = Color(0xFF1A140E),
    surfaceVariant = Color(0xFFE5DCCB),
    onSurfaceVariant = Color(0xFF6E6256),
    error = Color(0xFFA33B2C),
    outline = Color(0xFFD2C6B0),
)

private val OledScheme = InkScheme.copy(background = Color.Black, surface = Color(0xFF0A0A0A))

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ExpressiveType = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
)

private val ExpressiveDark = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF948F99),
)

@Composable
fun MoonTheme(theme: String, content: @Composable () -> Unit) {
    val scheme = when (theme) {
        "paper" -> PaperScheme
        "oled" -> ExpressiveDark.copy(background = Color.Black, surface = Color.Black)
        else -> ExpressiveDark
    }
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = ExpressiveType, content = content)
}

@Composable
fun prefersDark(theme: String): Boolean = theme != "paper" && (theme == "ink" || theme == "oled" || isSystemInDarkTheme())
