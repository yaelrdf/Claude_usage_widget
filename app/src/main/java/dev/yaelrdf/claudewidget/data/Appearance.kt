package dev.yaelrdf.claudewidget.data

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ColorStyle(val label: String) {
    PHONE("Match phone"),
    CLAUDE("Claude"),
    MONO("Mono"),
}

enum class Brightness(val label: String) {
    AUTO("Auto"),
    LIGHT("Light"),
    DARK("Dark"),
    BLACK("Black"),
}

enum class BarStyle(val label: String) {
    USAGE("By usage level"),
    ACCENT("Accent color"),
}

data class Appearance(
    val colors: ColorStyle = ColorStyle.PHONE,
    val brightness: Brightness = Brightness.AUTO,
    /** Widget background opacity, 0–100. */
    val opacity: Int = 90,
    val bars: BarStyle = BarStyle.USAGE,
)

/** Concrete colors for one brightness; the widget gets a light and a dark one. */
data class Palette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val track: Color,
    val low: Color,
    val mid: Color,
    val high: Color,
) {
    fun bar(percent: Int, style: BarStyle) = when {
        style == BarStyle.ACCENT -> accent
        percent >= 80 -> high
        percent >= 50 -> mid
        else -> low
    }
}

val dynamicColorAvailable get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Material color scheme for the chosen style; the app UI uses it directly and the widget derives a [Palette]. */
fun colorScheme(context: Context, style: ColorStyle, dark: Boolean): ColorScheme = when {
    style == ColorStyle.PHONE && dynamicColorAvailable ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    style == ColorStyle.MONO ->
        if (dark) darkColorScheme(
            primary = Color(0xFFFFFFFF), onPrimary = Color(0xFF000000),
            surface = Color(0xFF121212), onSurface = Color(0xFFEEEEEE),
            onSurfaceVariant = Color(0xFF9E9E9E), surfaceVariant = Color(0xFF2A2A2A),
        ) else lightColorScheme(
            primary = Color(0xFF222222), onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF666666), surfaceVariant = Color(0xFFE6E6E6),
        )
    // CLAUDE, and PHONE on Android < 12 where there are no system colors to follow.
    else ->
        if (dark) darkColorScheme(
            primary = Color(0xFFD97757), onPrimary = Color(0xFF1F1E1C),
            surface = Color(0xFF1F1E1C), onSurface = Color(0xFFEDEAE3),
            onSurfaceVariant = Color(0xFFA19D94), surfaceVariant = Color(0xFF34322E),
            surfaceContainer = Color(0xFF262522), background = Color(0xFF1F1E1C),
        ) else lightColorScheme(
            primary = Color(0xFFC15F3C), onPrimary = Color(0xFFFFFFFF),
            surface = Color(0xFFFAF9F5), onSurface = Color(0xFF1F1E1D),
            onSurfaceVariant = Color(0xFF6B6A65), surfaceVariant = Color(0xFFE8E6DC),
            surfaceContainer = Color(0xFFF0EEE6), background = Color(0xFFFAF9F5),
        )
}

/** Returns the (day, night) palettes for [appearance]; forced brightness makes both the same. */
fun palettes(context: Context, appearance: Appearance): Pair<Palette, Palette> {
    val light = palette(context, appearance, dark = false)
    val dark = palette(context, appearance, dark = true)
    return when (appearance.brightness) {
        Brightness.AUTO -> light to dark
        Brightness.LIGHT -> light to light
        Brightness.DARK, Brightness.BLACK -> dark to dark
    }
}

private fun palette(context: Context, appearance: Appearance, dark: Boolean): Palette {
    val scheme = colorScheme(context, appearance.colors, dark)
    val base = if (dark && appearance.brightness == Brightness.BLACK) Color.Black else scheme.surface
    val alpha = appearance.opacity.coerceIn(0, 100) / 100f
    return Palette(
        background = base.copy(alpha = alpha),
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        track = scheme.onSurface.copy(alpha = 0.16f),
        low = if (dark) Color(0xFF4CB782) else Color(0xFF2E9E6A),
        mid = if (dark) Color(0xFFE8A33D) else Color(0xFFC98A1E),
        high = if (dark) Color(0xFFE5484D) else Color(0xFFD93A3F),
    )
}
