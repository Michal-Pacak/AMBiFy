package pl.ambif.ambify.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The touch effect that comes with a theme (25.11: the effect is part of the theme, not chosen apart). */
enum class TouchEffect { RINGS, SPARKS, LASER, RAIN, HIT }

/** How a theme's headings and buttons are lettered (the fonts are in res/font). */
enum class ThemeFont { DEFAULT, ORBITRON, MONO, RUSSO }

/**
 * Every color of one theme. The app reads them through [Luxury] (the name stayed from the first theme), so a change
 * of [Luxury.palette] repaints every screen at once. Roles, not hues: "accent" is gold in Luxury, cyan in Neon,
 * phosphor green in Kod.
 */
data class Palette(
    val key: String,
    val light: Boolean,
    val background: Color,
    val surface: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val popupTop: Color,
    val popupBottom: Color,
    val text: Color,
    /** Text colors: never black or gray in Luxury (approved 2026-11-09) — warm champagne and sand instead. */
    val textMuted: Color,
    val disabled: Color,
    /** Grey text of the newer screens (lists, messages): one step quieter than [textMuted]. */
    val quiet: Color,
    val faint: Color,
    /** Outlines of chips and fields. */
    val outline: Color,
    /** The accent "metal": [accent] is the base, [accentLight] its highlight, [accentDeep]/[accentShadow] its dark side. */
    val accent: Color,
    val accentLight: Color,
    val accentHighlight: Color,
    val accentDeep: Color,
    val accentShadow: Color,
    val accentButton: Color,
    /** The accent for text and small marks on cards (checked for contrast). */
    val accentText: Color,
    val accentLink: Color,
    /** Text and icons on an accent button. */
    val onAccent: Color,
    val divider: Color,
    val borderColors: List<Color>,
    val popupBorderColors: List<Color>,
    /** Buttons: a vertical metal or a horizontal neon gradient. */
    val buttonColors: List<Color>,
    val buttonHorizontal: Boolean = false,
    /** Glow under cards and buttons (Neon, Kod, Gaming); transparent = none. */
    val glow: Color = Color.Transparent,
    // Message and state colors, darker on a light background.
    val ok: Color,
    val danger: Color,
    val error: Color,
    val info: Color,
    val warning: Color,
    val navBar: Color,
    val navAccent: Color,
    val navGold: Color,
    val effect: TouchEffect,
    val font: ThemeFont = ThemeFont.DEFAULT,
    /** Headings and buttons in capitals (Gaming). */
    val caps: Boolean = false,
    /** Gaming's cut corners: small top-left/bottom-right, big top-right/bottom-left. */
    val cutCorners: Boolean = false,
    val premium: Boolean = false,
    /** Own themes: the three colors (wallpaper patterns, the "Pasy" band), the band on cards and a wallpaper. */
    val trio: List<Color> = emptyList(),
    val band: Boolean = false,
    val wallpaper: pl.ambif.ambify.shared.Wallpaper? = null,
) {
    val cardBrush: Brush by lazy { Brush.verticalGradient(listOf(cardTop, cardBottom)) }
    val popupBrush: Brush by lazy { Brush.verticalGradient(listOf(popupTop, popupBottom)) }
    val borderBrush: Brush by lazy { Brush.linearGradient(borderColors) }
    val popupBorderBrush: Brush by lazy { Brush.linearGradient(popupBorderColors) }
    val buttonBrush: Brush by lazy { if (buttonHorizontal) Brush.horizontalGradient(buttonColors) else Brush.verticalGradient(buttonColors) }
}

/** The ready themes: three for everyone (Basic) and two Premium. */
object Palettes {
    val LUXURY = Palette(
        key = "luxury", light = false,
        background = Color(0xFF121214), surface = Color(0xFF19191C), cardTop = Color(0xFF232327), cardBottom = Color(0xFF19191C),
        popupTop = Color(0xFF2A2A2F), popupBottom = Color(0xFF1D1D21),
        text = Color(0xFFF1ECE0), textMuted = Color(0xFFD8CBA8), disabled = Color(0xFFC2B28A),
        quiet = Color(0xFFA9A9B1), faint = Color(0xFF85858D), outline = Color(0xFF3A3A40),
        accent = Color(0xFFD4AF37), accentLight = Color(0xFFF7E08A), accentHighlight = Color(0xFFFFF1B8), accentDeep = Color(0xFF8C7322),
        accentShadow = Color(0xFFA88A2A), accentButton = Color(0xFFC9A227), accentText = Color(0xFFE6C65C), accentLink = Color(0xFFD9B840),
        onAccent = Color(0xFF19191C), divider = Color(0x24E6C65C),
        borderColors = listOf(Color(0xFFF7E08A), Color(0xFF8C7322), Color(0xFFD4AF37), Color(0xFFA88A2A)),
        popupBorderColors = listOf(Color(0xFFFFF1B8), Color(0xFF9A8228), Color(0xFFEBCB5E), Color(0xFFB39330)),
        buttonColors = listOf(Color(0xFFF7E08A), Color(0xFFC9A227)),
        ok = Color(0xFF9AD7A4), danger = Color(0xFFE79A86), error = Color(0xFFFF8A80), info = Color(0xFF8EC3F0), warning = Color(0xFFF2C27B),
        navBar = Color(0xFF29251D), navAccent = Color(0xFFC9A85B), navGold = Color(0xFFD6B65F),
        effect = TouchEffect.RINGS,
    )

    val PEARL = Palette(
        key = "pearl", light = true,
        background = Color(0xFFF6F1E6), surface = Color(0xFFFBF8F1), cardTop = Color(0xFFFFFDF8), cardBottom = Color(0xFFF3ECDD),
        popupTop = Color(0xFFFFFDF8), popupBottom = Color(0xFFF1E9D8),
        text = Color(0xFF2A2418), textMuted = Color(0xFF6B5B3A), disabled = Color(0xFF8E7F60),
        quiet = Color(0xFF6E6A62), faint = Color(0xFF8A857B), outline = Color(0xFFD9CFBA),
        // On cream the "light gold" of the dark themes is a darker gold, else big numbers in it vanish (S10, 25.11).
        accent = Color(0xFFC9A227), accentLight = Color(0xFF9C7A14), accentHighlight = Color(0xFFB8901E), accentDeep = Color(0xFF8C7322),
        accentShadow = Color(0xFFA88A2A), accentButton = Color(0xFFC9A227), accentText = Color(0xFF7A6118), accentLink = Color(0xFF7A6118),
        onAccent = Color(0xFF2A2418), divider = Color(0x2E7A6118),
        borderColors = listOf(Color(0xFFE9CF7A), Color(0xFF8C7322), Color(0xFFC9A227), Color(0xFFA88A2A)),
        popupBorderColors = listOf(Color(0xFFE9CF7A), Color(0xFF8C7322), Color(0xFFC9A227), Color(0xFFA88A2A)),
        buttonColors = listOf(Color(0xFFE6C65C), Color(0xFFC9A227)),
        ok = Color(0xFF2E7D3A), danger = Color(0xFFB23B22), error = Color(0xFFB3261E), info = Color(0xFF1F5F95), warning = Color(0xFF8A5A00),
        navBar = Color(0xFFEFE6D2), navAccent = Color(0xFF7A6118), navGold = Color(0xFF7A6118),
        effect = TouchEffect.SPARKS,
    )

    val NEON = Palette(
        key = "neon", light = false,
        background = Color(0xFF0A0814), surface = Color(0xFF110D20), cardTop = Color(0xFF17122A), cardBottom = Color(0xFF0F0B1E),
        popupTop = Color(0xFF1C1633), popupBottom = Color(0xFF110D22),
        text = Color(0xFFF2EEFF), textMuted = Color(0xFFBDB3E6), disabled = Color(0xFF9A90C4),
        quiet = Color(0xFFA59EC2), faint = Color(0xFF7F78A0), outline = Color(0xFF3A2F5E),
        accent = Color(0xFF00F0FF), accentLight = Color(0xFF7DF8FF), accentHighlight = Color(0xFFC8FCFF), accentDeep = Color(0xFF7A2CFF),
        accentShadow = Color(0xFFFF2FD0), accentButton = Color(0xFFFF2FD0), accentText = Color(0xFF4DF3FF), accentLink = Color(0xFF4DF3FF),
        onAccent = Color(0xFF0A0814), divider = Color(0x2E4DF3FF),
        borderColors = listOf(Color(0xFF00F0FF), Color(0xFF7A2CFF), Color(0xFFFF2FD0), Color(0xFF00F0FF)),
        popupBorderColors = listOf(Color(0xFF00F0FF), Color(0xFF7A2CFF), Color(0xFFFF2FD0), Color(0xFF00F0FF)),
        buttonColors = listOf(Color(0xFF00F0FF), Color(0xFFFF2FD0)), buttonHorizontal = true, glow = Color(0x4700F0FF),
        ok = Color(0xFF7CF5B0), danger = Color(0xFFFF8AB8), error = Color(0xFFFF7A9A), info = Color(0xFF8EC3F0), warning = Color(0xFFFFD27A),
        navBar = Color(0xFF150F2A), navAccent = Color(0xFF4DF3FF), navGold = Color(0xFF4DF3FF),
        effect = TouchEffect.LASER, font = ThemeFont.ORBITRON,
    )

    /** Kod: the film's code — black, #003B00, #008F11 and the bright #00FF41 (Michał, 25.11: the dull green was wrong). */
    val CODE = Palette(
        key = "code", light = false, premium = true,
        background = Color(0xFF000500), surface = Color(0xFF010A03), cardTop = Color(0xFF031A08), cardBottom = Color(0xFF010C03),
        popupTop = Color(0xFF052410), popupBottom = Color(0xFF010C03),
        text = Color(0xFFCCFFD8), textMuted = Color(0xFF5FE07F), disabled = Color(0xFF3FAF5A),
        quiet = Color(0xFF4CC468), faint = Color(0xFF2F8F45), outline = Color(0xFF0B4A1A),
        accent = Color(0xFF00C832), accentLight = Color(0xFF00FF41), accentHighlight = Color(0xFFB4FFC6), accentDeep = Color(0xFF003B00),
        accentShadow = Color(0xFF008F11), accentButton = Color(0xFF008F11), accentText = Color(0xFF00FF41), accentLink = Color(0xFF00FF41),
        onAccent = Color(0xFF001A05), divider = Color(0x3300FF41),
        borderColors = listOf(Color(0xFF00FF41), Color(0xFF003B00), Color(0xFF008F11), Color(0xFF00C832)),
        popupBorderColors = listOf(Color(0xFF00FF41), Color(0xFF003B00), Color(0xFF008F11), Color(0xFF00C832)),
        buttonColors = listOf(Color(0xFF00FF41), Color(0xFF008F11)), glow = Color(0x4000FF41),
        ok = Color(0xFF00FF41), danger = Color(0xFFFFB070), error = Color(0xFFFF7A6A), info = Color(0xFF7FE0D8), warning = Color(0xFFE8E070),
        navBar = Color(0xFF010F04), navAccent = Color(0xFF00FF41), navGold = Color(0xFF00C832),
        effect = TouchEffect.RAIN, font = ThemeFont.MONO,
    )

    val GAMING = Palette(
        key = "gaming", light = false, premium = true,
        background = Color(0xFF0C0C0E), surface = Color(0xFF141316), cardTop = Color(0xFF1C1A1D), cardBottom = Color(0xFF121113),
        popupTop = Color(0xFF221F23), popupBottom = Color(0xFF151316),
        text = Color(0xFFF3F1F1), textMuted = Color(0xFFC4B8B8), disabled = Color(0xFFA89C9C),
        quiet = Color(0xFFA8A2A4), faint = Color(0xFF85808A), outline = Color(0xFF3A2C2C),
        accent = Color(0xFFFF2E3B), accentLight = Color(0xFFFF8A00), accentHighlight = Color(0xFFFFC07A), accentDeep = Color(0xFF7A1010),
        accentShadow = Color(0xFFB31B1B), accentButton = Color(0xFFFF8A00), accentText = Color(0xFFFF5A3C), accentLink = Color(0xFFFF7A4C),
        onAccent = Color(0xFF120606), divider = Color(0x2EFF5A3C),
        borderColors = listOf(Color(0xFFFF8A00), Color(0xFF7A1010), Color(0xFFFF2E3B), Color(0xFFB31B1B)),
        popupBorderColors = listOf(Color(0xFFFF8A00), Color(0xFF7A1010), Color(0xFFFF2E3B), Color(0xFFB31B1B)),
        buttonColors = listOf(Color(0xFFFF2E3B), Color(0xFFFF8A00)), buttonHorizontal = true, glow = Color(0x2EFF2E3B),
        ok = Color(0xFF9AD7A4), danger = Color(0xFFFF9A7A), error = Color(0xFFFF7A70), info = Color(0xFF8EC3F0), warning = Color(0xFFFFC46A),
        navBar = Color(0xFF1A1214), navAccent = Color(0xFFFF5A3C), navGold = Color(0xFFFF7A4C),
        effect = TouchEffect.HIT, font = ThemeFont.RUSSO, caps = true, cutCorners = true,
    )

    val ALL = listOf(LUXURY, PEARL, NEON, CODE, GAMING)
    fun of(key: String?): Palette? = ALL.firstOrNull { it.key == key }
}

/**
 * The current theme's colors. Every value is read from [palette] (snapshot state), so screens repaint when the
 * theme changes — e.g. on switching to a community with another theme.
 */
object Luxury {
    var palette: Palette by mutableStateOf(Palettes.LUXURY)

    val Background get() = palette.background
    val Surface get() = palette.surface
    val CardTop get() = palette.cardTop
    val CardBottom get() = palette.cardBottom
    val PopupTop get() = palette.popupTop
    val Text get() = palette.text
    val TextMuted get() = palette.textMuted
    val Disabled get() = palette.disabled
    val Quiet get() = palette.quiet
    val Faint get() = palette.faint
    val Outline get() = palette.outline

    val GoldBase get() = palette.accent
    val GoldLight get() = palette.accentLight
    val GoldHighlight get() = palette.accentHighlight
    val GoldDeep get() = palette.accentDeep
    val GoldShadow get() = palette.accentShadow
    val GoldButton get() = palette.accentButton
    val GoldText get() = palette.accentText
    val GoldLink get() = palette.accentLink
    val OnGold get() = palette.onAccent
    val Divider get() = palette.divider
    /** A card lit by the accent (the lesson running now). */
    val AccentTint get() = androidx.compose.ui.graphics.lerp(palette.cardTop, palette.accent, if (palette.light) 0.16f else 0.10f)

    val Ok get() = palette.ok
    val Danger get() = palette.danger
    val Error get() = palette.error
    val Info get() = palette.info
    val Warning get() = palette.warning

    private val families = mutableMapOf<ThemeFont, androidx.compose.ui.text.font.FontFamily>()

    /** Headings and buttons: the theme's lettering (Orbitron in Neon, typewriter in Kod, Russo One in Gaming). */
    val HeadingFont: androidx.compose.ui.text.font.FontFamily get() = families.getOrPut(palette.font) { headingFamily(palette.font) }

    /** A button's label as the theme writes it (capitals in Gaming). */
    fun label(text: String): String = if (palette.caps) text.uppercase() else text

    private val roundButton = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
    private val cutButton = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)
    private val roundSmall = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

    /** Buttons: a pill, or Gaming's cut corners. */
    val ButtonShape: androidx.compose.ui.graphics.Shape get() = if (palette.cutCorners) cutButton else roundButton
    val SmallButtonShape: androidx.compose.ui.graphics.Shape get() = if (palette.cutCorners) cutButton else roundSmall

    val CardBrush get() = palette.cardBrush
    val PopupBrush get() = palette.popupBrush
    val BorderBrush get() = palette.borderBrush
    val PopupBorderBrush get() = palette.popupBorderBrush
    val GoldFill get() = palette.buttonBrush
}
