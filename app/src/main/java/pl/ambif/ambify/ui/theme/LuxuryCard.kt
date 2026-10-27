package pl.ambif.ambify.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import pl.ambif.ambify.ui.icons.AppIcons
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CornerRadius = 20.dp

val LuxuryCardShape = RoundedCornerShape(CornerRadius)

/**
 * A point on the border of the card's bounding box moved onto the card's real outline: inside a corner square
 * the outline is the rounded arc, so the point goes to the arc (towards the corner's center).
 */
private fun onRoundedCorner(point: Offset, width: Float, height: Float, radius: Float): Offset {
    val cornerX = when {
        point.x < radius -> radius
        point.x > width - radius -> width - radius
        else -> return point
    }
    val cornerY = when {
        point.y < radius -> radius
        point.y > height - radius -> height - radius
        else -> return point
    }
    val center = Offset(cornerX, cornerY)
    val away = point - center
    val distance = away.getDistance()
    return if (distance == 0f) point else center + away * (radius / distance)
}

/** Whether cards "float" (Ustawienia → Aplikacja, and off when Android's animations are off). */
val LocalCardMotion = compositionLocalOf { true }

// The card is a rigid raft floating on water. Pressed, it tips towards the finger (the far side rises);
// the edge on that side touches the water and sends rings across it. Let go, it rocks to and fro with a
// shrinking swing before it settles: every time an edge dips it sends new, fainter rings, and every time the
// raft passes level the edges across the swing touch the water too and send a still weaker ring each.
private const val MaxTiltDegrees = 5f
private const val PressedScale = 0.985f
private const val PressMs = 160
/** After lifting the finger the raft stays tipped a moment before it rocks back. */
private const val ReleaseDelayMs = 120L
/** Low damping = several swings with a shrinking amplitude; low stiffness = slow, heavy swings (~1.1 s each). */
private const val RockDamping = 0.18f
private const val RockStiffness = 32f
/** A swing smaller than this no longer splashes. */
private const val SplashThreshold = 0.15f
/** The rings across the swing (when the raft passes level) are much weaker than the last dip, one ring per edge. */
private const val CrossSplashFactor = 0.2f
private const val CrossSplashRings = 1

/**
 * Graphite card with a gold gradient border and a shadow under it, so it stands out of the background.
 * The card itself is not a button: touched anywhere outside its controls it floats like a raft (see above),
 * with the rings drawn on the water under the cards ([WaterLayer]), never on the card. A touch on a control
 * inside (button, switch, link) does only what the control does — no rocking, no rings. Scrolling cancels it.
 * A card that opens something has a [LuxuryOpenButton].
 */
@Composable
fun LuxuryCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = LocalCardMotion.current
    val water = LocalWater.current
    val cornerPx = with(LocalDensity.current) { CornerRadius.toPx() }
    val scope = rememberCoroutineScope()
    // -1..1: how far the raft is tipped along [direction]; negative = tipped to the opposite side.
    val swing = remember { Animatable(0f) }
    val direction = remember { mutableStateOf(Offset(0f, 1f)) }
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

    /** Rings where the raft's edge in direction [towards] (from its center) touches the water. */
    fun splash(towards: Offset, strength: Float, rings: Int? = null) {
        val layout = coordinates.value ?: return
        val (dx, dy) = towards
        // From the center along the direction to the border of the card.
        val reach = min(if (dx != 0f) 0.5f / abs(dx) else Float.MAX_VALUE, if (dy != 0f) 0.5f / abs(dy) else Float.MAX_VALUE)
        val width = layout.size.width.toFloat()
        val height = layout.size.height.toFloat()
        val point = onRoundedCorner(Offset((0.5f + dx * reach) * width, (0.5f + dy * reach) * height), width, height, cornerPx)
        val windowPoint = layout.localToWindow(point)
        if (rings == null) water?.splash(windowPoint, strength) else water?.splash(windowPoint, strength, rings)
    }

    val floating = if (!motion) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            // The last pass: by now a control inside has taken (consumed) the touch if it was on one.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (down.isConsumed) return@awaitEachGesture
            val dx = down.position.x / size.width - 0.5f
            val dy = down.position.y / size.height - 0.5f
            val length = hypot(dx, dy)
            // A touch in the very middle pushes the near (bottom) edge down.
            direction.value = if (length < 0.05f) Offset(0f, 1f) else Offset(dx / length, dy / length)
            val pressed = scope.launch {
                swing.animateTo(min(1f, 0.4f + length * 1.2f), tween(PressMs))
                splash(direction.value, strength = swing.value)
            }
            var scrolled = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    scrolled = true
                    break
                }
                if (!change.pressed) break
            }
            scope.launch {
                pressed.join()
                if (!scrolled) delay(ReleaseDelayMs)
                val along = direction.value
                val across = Offset(-along.y, along.x)
                var lastVelocity = 0f
                var lastValue = swing.value
                var lastDip = swing.value
                swing.animateTo(0f, spring(dampingRatio = RockDamping, stiffness = RockStiffness)) {
                    // A turn of the swing = an edge at its lowest: it dips into the water.
                    if (lastVelocity != 0f && (lastVelocity > 0f) != (velocity > 0f) && abs(value) > SplashThreshold) {
                        splash(along * (if (value > 0f) 1f else -1f), strength = abs(value))
                        lastDip = abs(value)
                    }
                    // Passing level: the edges across the swing touch the water, more weakly.
                    if ((lastValue > 0f) != (value > 0f) && lastDip > SplashThreshold) {
                        splash(across, strength = lastDip * CrossSplashFactor, rings = CrossSplashRings)
                        splash(across * -1f, strength = lastDip * CrossSplashFactor, rings = CrossSplashRings)
                    }
                    lastVelocity = velocity
                    lastValue = value
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(floating)
            .graphicsLayer {
                val tipped = swing.value
                val (dx, dy) = direction.value
                rotationX = -dy * tipped * MaxTiltDegrees
                rotationY = dx * tipped * MaxTiltDegrees
                val scale = 1f - (1f - PressedScale) * abs(tipped)
                scaleX = scale
                scaleY = scale
                cameraDistance = 14f * density
                shadowElevation = (14f - 9f * abs(tipped)).dp.toPx()
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
                shape = LuxuryCardShape
                clip = true
            }
            // Inside the layer: the splash point follows the tilted, sunk card, not where it was before the touch.
            .onGloballyPositioned { coordinates.value = it }
            .background(Luxury.CardBrush)
            .border(1.dp, Luxury.BorderBrush, LuxuryCardShape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** The round gold button on a card that opens what the card is about (the card itself only floats). */
@Composable
fun LuxuryOpenButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Luxury.GoldBase.copy(alpha = 0.1f))
            .border(1.dp, Luxury.BorderBrush, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Forward, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
    }
}

/** Card section title: small gold capitals ("BASIC · W CENIE"). */
@Composable
fun LuxurySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Luxury.GoldText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
    )
}

/** Thin gold line between rows of a card. */
@Composable
fun LuxuryDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Luxury.Divider))
}
