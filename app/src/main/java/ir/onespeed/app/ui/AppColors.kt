package ir.onespeed.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Color tokens ported 1:1 from the OneSpeed HTML mockup's :root / .dark-mode
 * CSS variables, so the app matches the approved design exactly in both themes.
 */
private object Palette {
    // shared across both themes
    val sky = Color(0xFF0284C7)
    val skyDark = Color(0xFF38BDF8)
    val thyme = Color(0xFF65834D)
    val thymeDark = Color(0xFF7A9A60)
    val orange = Color(0xFFEA580C)
    val red = Color(0xFFDC2626)

    // light
    val bgLight = Color(0xFFF4F4F5)
    val phoneBgLight = Color(0xFFFFFFFF)
    val surfaceLight = Color(0xFFF8FAFC)
    val surface2Light = Color(0xFFEDF2F7)
    val lineLight = Color(0xFFCBD5E1)
    val textLight = Color(0xFF0F172A)
    val mutedLight = Color(0xFF475569)
    val muted2Light = Color(0xFF64748B)

    // dark
    val bgDark = Color(0xFF0D0F12)
    val phoneBgDark = Color(0xFF12151C)
    val surfaceDark = Color(0xFF161920)
    val surface2Dark = Color(0xFF222631)
    val lineDark = Color(0xFF2E3545)
    val textDark = Color(0xFFFFFFFF)
    val mutedDark = Color(0xFFE6DFD5)
    val muted2Dark = Color(0xFF94A3B8)
}

/** Holds the current theme preference, persisted across launches. */
object ThemeState {
    private const val PREFS = "onespeed_prefs"
    private const val KEY_DARK = "dark_mode"

    var isDark by mutableStateOf(false)
        private set

    fun init(ctx: Context) {
        isDark = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DARK, false)
    }

    fun toggle(ctx: Context) {
        isDark = !isDark
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DARK, isDark).apply()
    }
}

/** Resolves to the light or dark token set depending on [ThemeState.isDark]. */
object AppColors {
    val bg: Color @Composable get() = if (ThemeState.isDark) Palette.bgDark else Palette.bgLight
    val phoneBg: Color @Composable get() = if (ThemeState.isDark) Palette.phoneBgDark else Palette.phoneBgLight
    val surface: Color @Composable get() = if (ThemeState.isDark) Palette.surfaceDark else Palette.surfaceLight
    val surface2: Color @Composable get() = if (ThemeState.isDark) Palette.surface2Dark else Palette.surface2Light
    val line: Color @Composable get() = if (ThemeState.isDark) Palette.lineDark else Palette.lineLight
    val text: Color @Composable get() = if (ThemeState.isDark) Palette.textDark else Palette.textLight
    val muted: Color @Composable get() = if (ThemeState.isDark) Palette.mutedDark else Palette.mutedLight
    val muted2: Color @Composable get() = if (ThemeState.isDark) Palette.muted2Dark else Palette.muted2Light
    val sky: Color @Composable get() = if (ThemeState.isDark) Palette.skyDark else Palette.sky
    val thyme: Color @Composable get() = if (ThemeState.isDark) Palette.thymeDark else Palette.thyme

    // fixed accents
    val orange = Palette.orange
    val red = Palette.red

    // aliases kept for call sites written before this token pass
    val blue: Color @Composable get() = sky
    val aqua: Color @Composable get() = thyme
    val amber = Palette.orange

    val brandGradient: Brush @Composable get() = Brush.linearGradient(listOf(sky, thyme))
}
