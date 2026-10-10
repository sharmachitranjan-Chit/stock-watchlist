package com.chitranjan.watchlist.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.GainLoss
import com.chitranjan.watchlist.data.Palette
import com.chitranjan.watchlist.data.ThemeMode

/** Colours Material 3 doesn't name: gains, losses and a few neutrals. */
@Immutable
data class Extra(
    val dark: Boolean,
    val up: Color, val down: Color, val upSoft: Color, val downSoft: Color,
    val muted: Color, val textSoft: Color, val surface2: Color, val border: Color,
)

val LocalExtra = staticCompositionLocalOf { lightExtra(GainLoss.CLASSIC) }

private data class Accent(val light: Color, val dark: Color, val onLight: Color, val onDark: Color)

private val ACCENTS = mapOf(
    Palette.OCEAN to Accent(Color(0xFF1D5BD8), Color(0xFF7FA8FF), Color.White, Color(0xFF0A1020)),
    Palette.EMERALD to Accent(Color(0xFF0B7A6E), Color(0xFF4FD8C4), Color.White, Color(0xFF04211D)),
    Palette.SAFFRON to Accent(Color(0xFFB84A0C), Color(0xFFFFA45C), Color.White, Color(0xFF2A1203)),
    Palette.VIOLET to Accent(Color(0xFF6A33D1), Color(0xFFB9A0FF), Color.White, Color(0xFF170A33)),
    Palette.GRAPHITE to Accent(Color(0xFF1F2733), Color(0xFFE6EAF0), Color.White, Color(0xFF0B0F14)),
)

fun accentPreview(p: Palette, dark: Boolean): Color = ACCENTS[p]?.let { if (dark) it.dark else it.light } ?: Color(0xFF6750A4)

private val L_BG = Color(0xFFF4F6F9); private val L_SURF = Color(0xFFFFFFFF); private val L_SURF2 = Color(0xFFEDF0F4)
private val L_BORDER = Color(0xFFDFE4EA); private val L_TEXT = Color(0xFF0E1726); private val L_SOFT = Color(0xFF3A4556); private val L_MUTED = Color(0xFF5C6676)
private val D_BG = Color(0xFF0B0F14); private val D_SURF = Color(0xFF141A21); private val D_SURF2 = Color(0xFF1B232D)
private val D_BORDER = Color(0xFF26303C); private val D_TEXT = Color(0xFFEAEEF3); private val D_SOFT = Color(0xFFC3CBD5); private val D_MUTED = Color(0xFF98A2B0)

private fun moves(gl: GainLoss, dark: Boolean): Pair<Color, Color> = when (gl) {
    GainLoss.CLASSIC -> if (dark) Color(0xFF3FD584) to Color(0xFFFF6B6B) else Color(0xFF0B7F43) to Color(0xFFC62828)
    GainLoss.COLORBLIND -> if (dark) Color(0xFF79B0FF) to Color(0xFFFFA25A) else Color(0xFF1A5FD0) to Color(0xFFB54708)
}

private fun lightExtra(gl: GainLoss): Extra {
    val (u, d) = moves(gl, false)
    return Extra(false, u, d, u.copy(alpha = 0.10f), d.copy(alpha = 0.10f), L_MUTED, L_SOFT, L_SURF2, L_BORDER)
}

private fun darkExtra(gl: GainLoss): Extra {
    val (u, d) = moves(gl, true)
    return Extra(true, u, d, u.copy(alpha = 0.15f), d.copy(alpha = 0.15f), D_MUTED, D_SOFT, D_SURF2, D_BORDER)
}

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val a = ACCENTS[p] ?: ACCENTS.getValue(Palette.OCEAN)
    val primary = if (dark) a.dark else a.light
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = a.onDark, primaryContainer = primary.copy(alpha = 0.16f), onPrimaryContainer = primary,
        background = D_BG, onBackground = D_TEXT, surface = D_SURF, onSurface = D_TEXT,
        surfaceVariant = D_SURF2, onSurfaceVariant = D_MUTED, surfaceContainer = D_SURF, surfaceContainerHigh = D_SURF2,
        outline = D_BORDER, outlineVariant = D_BORDER, inverseSurface = D_TEXT, inverseOnSurface = D_BG,
    ) else lightColorScheme(
        primary = primary, onPrimary = a.onLight, primaryContainer = primary.copy(alpha = 0.10f), onPrimaryContainer = primary,
        background = L_BG, onBackground = L_TEXT, surface = L_SURF, onSurface = L_TEXT,
        surfaceVariant = L_SURF2, onSurfaceVariant = L_MUTED, surfaceContainer = L_SURF, surfaceContainerHigh = L_SURF2,
        outline = L_BORDER, outlineVariant = L_BORDER, inverseSurface = L_TEXT, inverseOnSurface = L_BG,
    )
}

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 15.5.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
)

@Composable
fun WatchlistTheme(mode: ThemeMode, palette: Palette, gainLoss: GainLoss, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val ctx = LocalContext.current
    val base = if (palette == Palette.YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        // Keep the app's own neutral backgrounds so prices stay readable on any wallpaper.
        scheme(Palette.OCEAN, dark).copy(
            primary = dyn.primary, onPrimary = dyn.onPrimary,
            primaryContainer = dyn.primary.copy(alpha = if (dark) 0.16f else 0.10f), onPrimaryContainer = dyn.primary,
        )
    } else scheme(if (palette == Palette.YOU) Palette.OCEAN else palette, dark)
    val extra = if (dark) darkExtra(gainLoss) else lightExtra(gainLoss)
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = base, typography = Type, content = content)
    }
}
