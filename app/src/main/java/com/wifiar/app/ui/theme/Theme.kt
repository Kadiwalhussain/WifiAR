package com.wifiar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Neon signal palette (futuristic AR product feel) ─────────────────────────
val NeonCyan = Color(0xFF00E5FF)
val NeonMint = Color(0xFF69F0AE)
val NeonAmber = Color(0xFFFFD740)
val NeonMagenta = Color(0xFFE040FB)
val NeonRose = Color(0xFFFF5252)
val DeepVoid = Color(0xFF070B12)
val PanelDark = Color(0xE6121A26)
val PanelLight = Color(0xF2FFFFFF)
val GlassStroke = Color(0x33FFFFFF)

val RssiStrong = Color(0xFF00C853)
val RssiMedium = Color(0xFFFFD600)
val RssiWeak = Color(0xFFFF6D00)
val RssiDead = Color(0xFFE51C23)

private val DarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF0A3A45),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = NeonMint,
    onSecondary = Color(0xFF00391A),
    secondaryContainer = Color(0xFF0D3B22),
    onSecondaryContainer = Color(0xFFB9F6CA),
    tertiary = NeonMagenta,
    onTertiary = Color(0xFF3A003F),
    tertiaryContainer = Color(0xFF4A1458),
    onTertiaryContainer = Color(0xFFF3E5F5),
    background = DeepVoid,
    onBackground = Color(0xFFE8EEF7),
    surface = Color(0xFF0E1520),
    onSurface = Color(0xFFE8EEF7),
    surfaceVariant = Color(0xFF1A2433),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF3D4F63),
    outlineVariant = Color(0xFF243040),
    error = NeonRose,
    onError = Color.White,
    errorContainer = Color(0xFF4A1010),
    onErrorContainer = Color(0xFFFFCDD2),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00838F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF002F35),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF0A2E0C),
    tertiary = Color(0xFF6A1B9A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1BEE7),
    onTertiaryContainer = Color(0xFF2A0A35),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF101418),
    surface = Color.White,
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFE3EDF5),
    onSurfaceVariant = Color(0xFF455A64),
    outline = Color(0xFF90A4AE),
    outlineVariant = Color(0xFFCFD8DC),
    error = Color(0xFFC62828),
    onError = Color.White,
)

/** Compact type scale — tighter than Material defaults so HUD feels dense, not chunky. */
private val WifiArTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 15.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp),
)

@Composable
fun WifiARTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WifiArTypography,
        content = content,
    )
}
