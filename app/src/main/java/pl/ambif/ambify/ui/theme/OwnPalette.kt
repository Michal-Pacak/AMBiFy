package pl.ambif.ambify.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import pl.ambif.ambify.shared.BorderStyle
import pl.ambif.ambify.shared.OwnTheme
import pl.ambif.ambify.shared.Themes

/**
 * An own theme (editor, canvas board 4) as a full [Palette]: a few knobs instead of 25 colors. Text and the accent
 * are checked against the card: below 4.5:1 they are lightened (dark theme) or darkened (light one) until readable.
 */
object OwnPalette {
    private const val MIN_CONTRAST = 4.5

    fun from(theme: OwnTheme): Palette {
        val dark = theme.dark
        val h = theme.bgHue.toFloat()
        val s = theme.bgSat / 100f
        val bg = hsl(h, s * 0.5f, if (dark) 0.07f else 0.95f)
        val cardTop = hsl(h, s * (if (dark) 0.45f else 0.5f), if (dark) 0.14f else 0.99f)
        val cardBottom = hsl(h, s * (if (dark) 0.45f else 0.4f), if (dark) 0.10f else 0.91f)
        val colors = theme.colors.mapNotNull { parse(it) }.ifEmpty { listOf(Palettes.LUXURY.accent) }
        val c1 = colors[0]
        val c2 = colors.getOrElse(1) { c1 }
        val c3 = colors.getOrElse(2) { c1 }
        val text = guard(if (dark) hsl(40f, 0.10f, 0.93f) else hsl(h, 0.25f, 0.13f), cardTop, dark)
        val muted = guard(if (dark) hsl(40f, 0.14f, 0.74f) else hsl(h, 0.22f, 0.34f), cardTop, dark)
        val accentText = guard(c1, cardTop, dark)
        val onAccent = if (contrast(Color(0xFF141416), c2) >= contrast(Color.White, c2)) Color(0xFF141416) else Color.White
        val base = if (dark) Palettes.LUXURY else Palettes.PEARL
        val (border, buttons, horizontal) = when (theme.style) {
            BorderStyle.METAL -> Triple(listOf(lighter(c1, 0.24f), darker(c1, 0.20f), c1, darker(c1, 0.10f)), listOf(lighter(c2, 0.18f), c2), false)
            BorderStyle.FLAT, BorderStyle.STRIPES -> Triple(listOf(c1, c1), listOf(c2, c2), false)
            BorderStyle.NEON -> Triple(listOf(c1, c3, c1), listOf(c2, c3), true)
        }
        return base.copy(
            key = Themes.custom(theme.id), light = !dark, premium = true,
            background = bg, surface = hsl(h, s * 0.45f, if (dark) 0.10f else 0.97f), cardTop = cardTop, cardBottom = cardBottom,
            popupTop = hsl(h, s * 0.45f, if (dark) 0.17f else 0.99f), popupBottom = hsl(h, s * 0.45f, if (dark) 0.11f else 0.93f),
            text = text, textMuted = muted, disabled = lerp(muted, bg, 0.15f), quiet = lerp(muted, bg, 0.25f), faint = lerp(muted, bg, 0.45f),
            outline = if (dark) lerp(bg, Color.White, 0.18f) else lerp(bg, Color.Black, 0.15f),
            accent = c1, accentLight = lighter(c1, 0.24f), accentHighlight = lighter(c1, 0.36f), accentDeep = darker(c1, 0.20f),
            accentShadow = darker(c1, 0.10f), accentButton = c2, accentText = accentText, accentLink = accentText, onAccent = onAccent,
            divider = accentText.copy(alpha = 0.16f), borderColors = border, popupBorderColors = border,
            buttonColors = buttons, buttonHorizontal = horizontal,
            glow = if (theme.style == BorderStyle.NEON) c1.copy(alpha = 0.3f) else Color.Transparent,
            navBar = lerp(bg, c1, 0.08f), navAccent = accentText, navGold = accentText,
            effect = effectOf(theme.effect), font = ThemeFont.DEFAULT, caps = false, cutCorners = false,
            trio = listOf(c1, c2, c3), band = theme.style == BorderStyle.STRIPES, wallpaper = theme.wallpaper,
        )
    }

    /** How readable [a] is on [b] (WCAG: 1 to 21). */
    fun contrast(a: Color, b: Color): Double {
        val x = a.luminance() + 0.05
        val y = b.luminance() + 0.05
        return maxOf(x, y) / minOf(x, y)
    }

    /** [color] lightened (dark theme) or darkened until it reads on [against]. */
    fun guard(color: Color, against: Color, dark: Boolean): Color {
        val (h, s, l0) = toHsl(color)
        var l = l0
        var c = color
        var i = 0
        while (contrast(c, against) < MIN_CONTRAST && i < 50) {
            l = if (dark) minOf(0.98f, l + 0.02f) else maxOf(0.04f, l - 0.02f)
            c = hsl(h, s, l)
            i++
        }
        return c
    }

    fun parse(hex: String): Color? = runCatching { Color(0xFF000000 or hex.removePrefix("#").toLong(16)) }.getOrNull()

    fun hex(color: Color): String {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun effectOf(key: String): TouchEffect = when (key) {
        "sparks" -> TouchEffect.SPARKS
        "laser" -> TouchEffect.LASER
        "rain" -> TouchEffect.RAIN
        "hit" -> TouchEffect.HIT
        else -> TouchEffect.RINGS
    }

    fun hsl(h: Float, s: Float, l: Float): Color = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

    private fun lighter(c: Color, by: Float): Color = toHsl(c).let { (h, s, l) -> hsl(h, s, minOf(0.94f, l + by)) }
    private fun darker(c: Color, by: Float): Color = toHsl(c).let { (h, s, l) -> hsl(h, s, maxOf(0.06f, l - by)) }

    private fun toHsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l)
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return Triple(h, s, l)
    }
}
