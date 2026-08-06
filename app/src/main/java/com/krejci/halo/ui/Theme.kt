package com.krejci.halo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.krejci.halo.data.MetricType

// ─── Halo · Refined Midnight ────────────────────────────────────────────────
// The base palette evolves the original teal/navy DNA: a deeper ink base and a
// slightly richer cyan. The *accent* (primary) is user-selectable — see [Accent].

// Dark surfaces
val InkDark = Color(0xFF060A17)        // background — deeper than before
val SurfaceDark = Color(0xFF0F1828)    // cards / nav bar
val SurfaceVariantDark = Color(0xFF17223B)
private val OnDark = Color(0xFFEAF0FF)
private val OnDarkDim = Color(0xFF8CA0C6)
private val OutlineDark = Color(0xFF2B3A5E)

// Light surfaces
private val InkLight = Color(0xFFEEF2FB)
private val SurfaceLight = Color.White
private val SurfaceVariantLight = Color(0xFFE7EEFB)
private val OnLight = Color(0xFF0C1630)
private val OnLightDim = Color(0xFF4D5D80)
private val OutlineLight = Color(0xFFC7D2E8)

// Warm secondary shared by every accent (used for the heart/secondary tint).
val Coral = Color(0xFFFB7AA8)

/**
 * Selectable accent (the app's primary color). `dark`/`light` are the primary in each theme;
 * `swatch` is what the picker shows. The default is [Accent.CYAN] — the classic Halo teal.
 */
enum class Accent(val label: String, val dark: Color, val light: Color) {
    CYAN("Cyan", Color(0xFF22D3EE), Color(0xFF0E97B4)),
    VIOLET("Violet", Color(0xFFA78BFA), Color(0xFF7C5CE0)),
    EMBER("Ember", Color(0xFFFB923C), Color(0xFFD9720F)),
    MINT("Mint", Color(0xFF34D399), Color(0xFF0F9A6B)),
    ROSE("Rose", Color(0xFFFB7185), Color(0xFFD64C63));

    /** Representative color for the picker swatch (uses the dark/bright variant). */
    val swatch: Color get() = dark

    companion object {
        val DEFAULT = CYAN
        fun from(name: String?): Accent = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** How the app decides light vs dark. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark");

    companion object {
        val DEFAULT = SYSTEM
        fun from(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

private fun darkColors(accent: Accent) = darkColorScheme(
    primary = accent.dark,
    onPrimary = Color(0xFF04121A),
    secondary = Coral,
    onSecondary = Color(0xFF2A0713),
    background = InkDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnDarkDim,
    outline = OutlineDark,
)

private fun lightColors(accent: Accent) = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    secondary = Color(0xFFC03D86),
    onSecondary = Color.White,
    background = InkLight,
    onBackground = OnLight,
    surface = SurfaceLight,
    onSurface = OnLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnLightDim,
    outline = OutlineLight,
)

@Composable
fun HaloTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: Accent = Accent.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(colorScheme = if (dark) darkColors(accent) else lightColors(accent), content = content)
}

/** Per-metric accent colors (semantic — independent of the app accent). */
fun metricColor(m: MetricType): Color = when (m) {
    MetricType.HR -> Color(0xFFF472B6)
    MetricType.SPO2 -> Color(0xFF22D3EE)
    MetricType.HRV -> Color(0xFFA78BFA)
    MetricType.STRESS -> Color(0xFFFBBF24)
    MetricType.STEPS -> Color(0xFF34D399)
}

val SleepColor = Color(0xFF818CF8)
