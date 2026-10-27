package pl.ambif.ambify.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** The Luxury theme (approved 2026-10-27): graphite surfaces and metallic gold, the same on every phone. */
object Luxury {
    val Background = Color(0xFF121214)
    val Surface = Color(0xFF19191C)
    val CardTop = Color(0xFF232327)
    val CardBottom = Color(0xFF19191C)
    val PopupTop = Color(0xFF2A2A2F)
    val Text = Color(0xFFF1ECE0)
    val TextMuted = Color(0xFFA9A9B1)
    val Disabled = Color(0xFF85858D)

    /** Metallic gold: [Base] is the classic gold, [Light] its highlight, [Deep] and [Shadow] its dark side. */
    val GoldBase = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFF7E08A)
    val GoldHighlight = Color(0xFFFFF1B8)
    val GoldDeep = Color(0xFF8C7322)
    val GoldShadow = Color(0xFFA88A2A)
    val GoldButton = Color(0xFFC9A227)
    /** Gold for text and small marks on graphite. */
    val GoldText = Color(0xFFE6C65C)
    val GoldLink = Color(0xFFD9B840)
    val Divider = Color(0x24E6C65C)

    val CardBrush = Brush.verticalGradient(listOf(CardTop, CardBottom))
    val PopupBrush = Brush.verticalGradient(listOf(PopupTop, Color(0xFF1D1D21)))
    val BorderBrush = Brush.linearGradient(listOf(GoldLight, GoldDeep, GoldBase, GoldShadow))
    val PopupBorderBrush = Brush.linearGradient(listOf(GoldHighlight, Color(0xFF9A8228), Color(0xFFEBCB5E), Color(0xFFB39330)))
    val GoldFill = Brush.verticalGradient(listOf(GoldLight, GoldButton))
}
