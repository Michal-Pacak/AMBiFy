package pl.ambif.ambify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Always the Luxury colors: no wallpaper-based dynamic colors, no light variant. */
private val LuxuryColorScheme = darkColorScheme(
    primary = Luxury.GoldBase,
    onPrimary = Luxury.CardBottom,
    primaryContainer = Color(0xFF2A2617),
    onPrimaryContainer = Luxury.GoldLight,
    secondary = Luxury.GoldLink,
    onSecondary = Luxury.CardBottom,
    secondaryContainer = Color(0xFF2A2617),
    onSecondaryContainer = Luxury.GoldText,
    tertiary = Luxury.GoldText,
    onTertiary = Luxury.CardBottom,
    background = Luxury.Background,
    onBackground = Luxury.Text,
    surface = Luxury.Surface,
    onSurface = Luxury.Text,
    onSurfaceVariant = Luxury.TextMuted,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF161618),
    surfaceContainer = Color(0xFF1D1D21),
    surfaceContainerHigh = Luxury.CardTop,
    surfaceContainerHighest = Luxury.PopupTop,
    outline = Luxury.GoldDeep,
    outlineVariant = Color(0xFF3A3424),
    error = Color(0xFFFF8A80),
)

@Composable
fun AMBiFyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LuxuryColorScheme,
        typography = Typography,
        content = content,
    )
}
