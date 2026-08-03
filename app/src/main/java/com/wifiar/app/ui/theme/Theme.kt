package com.wifiar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WifiBlue = Color(0xFF1565C0)
private val WifiBlueDark = Color(0xFF0D47A1)
private val SignalGreen = Color(0xFF2E7D32)
private val SignalAmber = Color(0xFFF9A825)
private val SignalRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = WifiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = WifiBlueDark,
    secondary = SignalGreen,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    error = SignalRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = WifiBlueDark,
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF81C784),
    background = Color(0xFF101418),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6),
    error = Color(0xFFEF9A9A),
)

val RssiStrong = SignalGreen
val RssiMedium = SignalAmber
val RssiWeak = SignalRed

@Composable
fun WifiARTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
