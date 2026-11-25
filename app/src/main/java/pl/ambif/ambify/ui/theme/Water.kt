package pl.ambif.ambify.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Rings on the water from a lazily rocking raft (not a splash from a height): each dip sends a few rings,
// far apart, spreading slowly (about 27 dp/s) at an almost steady pace, thinning and fading as they go.
// A weaker dip (a smaller sway) gives fainter rings.
private const val RingsPerSplash = 2
private const val RingDelayMs = 900L
private const val RingDurationMs = 4500
private val RingMaxRadius = 120.dp
private const val RingStartAlpha = 0.55f
private val RingStartWidth = 2.dp
private val RingEndWidth = 0.6.dp
private val RingEasing = CubicBezierEasing(0.25f, 0.1f, 0.35f, 1f)

internal class WaterRing(val center: Offset, val strength: Float, val progress: Animatable<Float, AnimationVector1D>)

/** One touch effect of a theme other than Luxury: drawn once, over the card, where it was touched. */
internal class Burst(
    val effect: TouchEffect,
    val point: Offset,
    val card: Rect,
    val seed: Int,
    val durationMs: Int,
    /** 0..1, like the rings: a weaker dip of the card gives a fainter effect. */
    val strength: Float,
    /** Under the cards (from where a card's edge touches the background), or over them (Gaming's hit). */
    val under: Boolean,
    val progress: Animatable<Float, AnimationVector1D>,
)

/** The "water" under the screen content: Luxury cards float above it and their edges send rings across it. */
class WaterSurface internal constructor(private val scope: CoroutineScope) {
    internal val rings = mutableStateListOf<WaterRing>()
    internal val bursts = mutableStateListOf<Burst>()
    internal var origin = Offset.Zero

    /**
     * The theme's touch effect at [windowPoint] for a card occupying [card] (window coordinates): [under] the cards
     * where its edge touches the background (like the rings), or over the card where the finger is (Gaming).
     */
    fun burst(windowPoint: Offset, card: Rect, strength: Float = 1f, under: Boolean = true) {
        val effect = Luxury.palette.effect
        if (effect == TouchEffect.RINGS) return
        val duration = when (effect) {
            TouchEffect.SPARKS -> 800
            TouchEffect.LASER -> 650
            TouchEffect.RAIN -> 2200
            TouchEffect.HIT -> 950
            TouchEffect.RINGS -> 0
        }
        // Not more than a few at once: a quick finger should not pile them up.
        while (bursts.size >= 8) bursts.removeAt(0)
        val burst = Burst(effect, windowPoint, card, Random.nextInt(), duration, strength.coerceIn(0f, 1f), under, Animatable(0f))
        bursts.add(burst)
        scope.launch {
            burst.progress.animateTo(1f, tween(duration, easing = LinearEasing))
            bursts.remove(burst)
        }
    }

    /** [count] rings from [windowPoint] (a card edge touching the water); [strength] 0..1 scales how visible they are. */
    fun splash(windowPoint: Offset, strength: Float, count: Int = RingsPerSplash) {
        repeat(count) { index ->
            scope.launch {
                delay(index * RingDelayMs)
                val ring = WaterRing(windowPoint, strength.coerceIn(0f, 1f), Animatable(0f))
                rings.add(ring)
                ring.progress.animateTo(1f, tween(RingDurationMs, easing = RingEasing))
                rings.remove(ring)
            }
        }
    }
}

val LocalWater = staticCompositionLocalOf<WaterSurface?> { null }

/**
 * Draws the rings behind [content]. Screens inside must not paint their own background over it (they use a
 * transparent surface; the background color comes from the Scaffold under this layer).
 */
@Composable
fun WaterLayer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val water = remember { WaterSurface(scope) }
    val text = rememberTextMeasurer()
    Box(
        modifier = modifier
            .onGloballyPositioned { water.origin = it.positionInWindow() }
            // The other themes' effects are drawn over the cards (a spark under a card would not be seen).
            .drawWithContent {
                drawContent()
                water.bursts.forEach { if (!it.under) drawBurst(it, it.progress.value, water.origin, text) }
            }
            .drawBehind {
                // Under the cards: sparks, laser and glyphs from the cards' edges, like the rings.
                water.bursts.forEach { if (it.under) drawBurst(it, it.progress.value, water.origin, text) }
                water.rings.forEach { ring ->
                    val progress = ring.progress.value
                    drawCircle(
                        color = Luxury.GoldLight.copy(alpha = RingStartAlpha * ring.strength * (1f - progress)),
                        radius = progress * RingMaxRadius.toPx(),
                        center = ring.center - water.origin,
                        style = Stroke(width = (RingStartWidth + (RingEndWidth - RingStartWidth) * progress).toPx()),
                    )
                }
            },
    ) {
        CompositionLocalProvider(LocalWater provides water) { content() }
    }
}

private const val Glyphs = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄ0123456789ZXCVB"

private fun DrawScope.drawBurst(b: Burst, t: Float, origin: Offset, text: TextMeasurer) {
    val p = b.point - origin
    val card = b.card.translate(-origin)
    val rnd = Random(b.seed)
    val pal = Luxury.palette
    val k = 0.45f + 0.55f * b.strength
    when (b.effect) {
        TouchEffect.SPARKS -> {
            // Gold sparks flying out and falling a little, shrinking as they burn out.
            repeat(14) { i ->
                val angle = (i / 14f) * 2f * PI.toFloat() + rnd.nextFloat() * 0.4f
                val speed = (70 + rnd.nextInt(80)).dp.toPx()
                val d = speed * t
                val x = p.x + cos(angle) * d
                val y = p.y + sin(angle) * d + 60.dp.toPx() * t * t
                val r = (2.6f - 2f * t).coerceAtLeast(0.4f).dp.toPx()
                val c = if (i % 3 == 0) pal.accentHighlight else if (i % 3 == 1) pal.accentLight else pal.accent
                drawCircle(c.copy(alpha = (k * (1f - t)).coerceIn(0f, 1f)), r, Offset(x, y))
                if (i % 4 == 0) drawLine(c.copy(alpha = 0.6f * k * (1f - t)), Offset(x - r * 2.5f, y), Offset(x + r * 2.5f, y), strokeWidth = r * 0.5f)
            }
        }
        TouchEffect.LASER -> {
            // A beam across the card at the touch, fast out to both edges, then fading; a glow under it.
            val grow = (t / 0.3f).coerceAtMost(1f)
            val fade = k * if (t < 0.45f) 1f else (1f - (t - 0.45f) / 0.55f).coerceIn(0f, 1f)
            // Two beams cross where the card's edge touches the background (canvas "Neon · Laser"); the card covers
            // their middle, so they shoot out from under it.
            val reach = 150.dp.toPx() * grow
            val left = p.x - reach
            val right = p.x + reach
            val top = p.y - reach
            val bottom = p.y + reach
            val across = Brush.horizontalGradient(listOf(pal.accentShadow, pal.accent, pal.accentShadow), startX = p.x - 150.dp.toPx(), endX = p.x + 150.dp.toPx())
            val upDown = Brush.verticalGradient(listOf(pal.accentShadow, pal.accent, pal.accentShadow), startY = p.y - 150.dp.toPx(), endY = p.y + 150.dp.toPx())
            drawLine(across, Offset(left, p.y), Offset(right, p.y), strokeWidth = 10.dp.toPx(), alpha = 0.22f * fade, cap = StrokeCap.Round)
            drawLine(upDown, Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 10.dp.toPx(), alpha = 0.22f * fade, cap = StrokeCap.Round)
            drawLine(across, Offset(left, p.y), Offset(right, p.y), strokeWidth = 2.5.dp.toPx(), alpha = fade, cap = StrokeCap.Round)
            drawLine(upDown, Offset(p.x, top), Offset(p.x, bottom), strokeWidth = 2.5.dp.toPx(), alpha = fade, cap = StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = 0.9f * fade), 3.dp.toPx(), p)
        }
        TouchEffect.RAIN -> {
            // Five columns of glyphs falling from the touch; each glyph lights up and fades on its own.
            val total = b.durationMs
            val now = t * total
            val col = 22.dp.toPx()
            val row = 18.dp.toPx()
            val style = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            // On a side edge the strip between the card and the screen edge is about one column wide: the first
            // column falls in the middle of that strip, the others go inwards (under the card). On the top and bottom
            // edges the columns spread across the touch as before.
            val toLeft = abs(p.x - card.left)
            val toRight = abs(p.x - card.right)
            val side = minOf(toLeft, toRight) < minOf(abs(p.y - card.top), abs(p.y - card.bottom))
            for (c in 0 until 5) {
                val x = when {
                    !side -> p.x + (c - 2) * col
                    toLeft < toRight -> card.left / 2f + c * col
                    else -> (card.right + size.width) / 2f - c * col
                }
                val rows = 7 + rnd.nextInt(4)
                val start = rnd.nextInt(250)
                for (j in 0 until rows) {
                    val at = start + j * 75
                    val life = 650 + rnd.nextInt(720)
                    val glyph = Glyphs[rnd.nextInt(Glyphs.length)].toString()
                    val k = (now - at) / life
                    if (k < 0f || k > 1f) continue
                    val color = when {
                        k < 0.12f -> lerp(Color(0xFFE4FFE8), pal.accentLight, k / 0.12f)
                        else -> lerp(pal.accentText, pal.accentDeep, (k - 0.12f) / 0.88f)
                    }
                    val alpha = b.strength.let { 0.45f + 0.55f * it } * if (k < 0.7f) 1f else (1f - (k - 0.7f) / 0.3f)
                    val y = p.y - row * 2 + j * row
                    val layout = text.measure(glyph, style)
                    scale(-1f, 1f, Offset(x, y + layout.size.height / 2f)) {
                        drawText(layout, color.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x - layout.size.width / 2f, y))
                    }
                }
            }
        }
        TouchEffect.HIT -> {
            // A hit: four slashes out from the touch and "+10 XP" rising.
            val slash = (t / 0.35f).coerceAtMost(1f)
            val slashFade = (1f - (t - 0.2f) / 0.4f).coerceIn(0f, 1f)
            listOf(45f, 135f, 225f, 315f).forEach { deg ->
                val a = deg * PI.toFloat() / 180f
                val from = 8.dp.toPx() + 10.dp.toPx() * slash
                val to = from + 24.dp.toPx() * slash
                drawLine(
                    pal.accentLight.copy(alpha = slashFade), Offset(p.x + cos(a) * from, p.y + sin(a) * from), Offset(p.x + cos(a) * to, p.y + sin(a) * to),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Square,
                )
            }
            drawCircle(pal.accent.copy(alpha = 0.5f * slashFade), 8.dp.toPx() * (1f + slash), p, style = Stroke(2.dp.toPx()))
            val rise = 34.dp.toPx() * t
            val xpAlpha = if (t < 0.65f) 1f else (1f - (t - 0.65f) / 0.35f)
            val layout = text.measure("+10 XP", TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Luxury.HeadingFont))
            drawText(layout, pal.accentLight.copy(alpha = xpAlpha.coerceIn(0f, 1f)), Offset(p.x - layout.size.width / 2f, p.y - 24.dp.toPx() - rise))
        }
        TouchEffect.RINGS -> Unit
    }
}
