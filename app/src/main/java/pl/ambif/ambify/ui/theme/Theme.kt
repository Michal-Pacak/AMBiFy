package pl.ambif.ambify.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Material's colors from the theme's palette: no wallpaper-based dynamic colors. */
private fun schemeOf(p: Palette): ColorScheme {
    val container = lerp(p.surface, p.accent, if (p.light) 0.14f else 0.10f)
    val mix = { f: Float -> lerp(p.background, p.cardTop, f) }
    return if (p.light) lightColorScheme(
        primary = p.accentText, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = p.text,
        secondary = p.accentLink, onSecondary = Color.White, secondaryContainer = container, onSecondaryContainer = p.text,
        tertiary = p.accentText, onTertiary = Color.White,
        background = p.background, onBackground = p.text, surface = p.surface, onSurface = p.text, onSurfaceVariant = p.textMuted,
        surfaceContainerLowest = Color.White, surfaceContainerLow = mix(0.3f), surfaceContainer = mix(0.6f),
        surfaceContainerHigh = p.cardTop, surfaceContainerHighest = p.popupTop,
        outline = p.accentDeep, outlineVariant = p.outline, error = p.error,
    ) else darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = container, onPrimaryContainer = p.accentLight,
        secondary = p.accentLink, onSecondary = p.onAccent, secondaryContainer = container, onSecondaryContainer = p.accentText,
        tertiary = p.accentText, onTertiary = p.onAccent,
        background = p.background, onBackground = p.text, surface = p.surface, onSurface = p.text, onSurfaceVariant = p.textMuted,
        surfaceContainerLowest = lerp(p.background, Color.Black, 0.2f), surfaceContainerLow = mix(0.3f), surfaceContainer = mix(0.6f),
        surfaceContainerHigh = p.cardTop, surfaceContainerHighest = p.popupTop,
        outline = p.accentDeep, outlineVariant = p.outline, error = p.error,
    )
}

@Composable
fun AMBiFyTheme(content: @Composable () -> Unit) {
    val palette = Luxury.palette
    val scheme = remember(palette) { schemeOf(palette) }
    val typography = remember(palette.font) { typographyFor(palette.font) }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text without its own color takes the theme's text color everywhere, also outside a Scaffold/Surface.
        CompositionLocalProvider(LocalContentColor provides palette.text, content = content)
    }
}
