package pl.ambif.ambify.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Luxury theme has no black or gray text (Michał, 2026-11-09): text colors are cream, champagne, sand or gold,
 * readable on every graphite surface.
 */
class TextColorRulesTest {
    private val textColors = mapOf(
        "Text" to Luxury.Text,
        "TextMuted" to Luxury.TextMuted,
        "Disabled" to Luxury.Disabled,
        "GoldText" to Luxury.GoldText,
        "GoldLink" to Luxury.GoldLink,
    )
    private val surfaces = mapOf("Background" to Luxury.Background, "CardTop" to Luxury.CardTop, "PopupTop" to Luxury.PopupTop)

    @Test
    fun textColorsAreReadableOnEverySurface() {
        for ((name, text) in textColors) for ((surfaceName, surface) in surfaces) {
            val ratio = contrast(text, surface)
            assertTrue("$name on $surfaceName: contrast %.1f < 4.5".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun textColorsAreNotGray() {
        for ((name, color) in textColors) assertTrue("$name is gray", !isNeutral(color.toArgb()))
    }

    /** Every `color = …` in the app's code: no black or gray text sneaks in as a literal. */
    @Test
    fun noBlackOrGrayTextInTheCode() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("sources not found from ${File(".").absolutePath}", sources.isNotEmpty())
        val named = Regex("""\bcolor\s*=[^,)]*\bColor\.(Black|Gray|DarkGray|LightGray)\b""")
        val literal = Regex("""\bcolor\s*=[^,]*?Color\(0x([0-9A-Fa-f]{8})\)""")
        val problems = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val gray = literal.findAll(line).any { isNeutral(it.groupValues[1].toLong(16).toInt()) }
                if (named.containsMatchIn(line) || gray) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("Black or gray text color:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    /** Black, white-ish gray or any gray: red, green and blue nearly equal (cream white #F1ECE0 is not). */
    private fun isNeutral(argb: Int): Boolean {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b) <= 16 && maxOf(r, g, b) < 0xF0
    }

    private fun Color.toArgb(): Int =
        ((alpha * 255).toInt() shl 24) or ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()
}
