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

/** The "water" under the screen content: Luxury cards float above it and their edges send rings across it. */
class WaterSurface internal constructor(private val scope: CoroutineScope) {
    internal val rings = mutableStateListOf<WaterRing>()
    internal var origin = Offset.Zero

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
    Box(
        modifier = modifier
            .onGloballyPositioned { water.origin = it.positionInWindow() }
            .drawBehind {
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
