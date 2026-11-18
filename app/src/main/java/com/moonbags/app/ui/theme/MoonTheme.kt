package com.moonbags.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun MoonTheme(theme: String, materialYou: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = materialYou && Build.VERSION.SDK_INT >= 31
    val scheme = when {
        dynamic && (theme != "paper") -> dynamicDarkColorScheme(context)
        dynamic && theme == "paper" -> dynamicLightColorScheme(context)
        theme == "paper" -> PaperScheme
        theme == "oled" -> OledScheme
        else -> InkScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun prefersDark(theme: String): Boolean = theme != "paper" && (theme == "ink" || theme == "oled" || isSystemInDarkTheme())
