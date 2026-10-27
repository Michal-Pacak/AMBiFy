package pl.ambif.ambify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import pl.ambif.ambify.shared.Avatar
import pl.ambif.ambify.shared.AvatarShape
import pl.ambif.ambify.shared.Avatars
import pl.ambif.ambify.ui.theme.Luxury

/**
 * A person's icon: initials on their color, in their shape, with a thin gold rim. The only place that draws a
 * person, so they look the same on every screen and in every module. Without an [avatar] from the server
 * (e.g. offline data from an older version) it falls back to initials in a circle.
 */
@Composable
fun PersonAvatar(name: String, avatar: Avatar?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val shape = (avatar?.shape ?: AvatarShape.CIRCLE).toShape()
    val color = avatar?.color?.let(::parseHex) ?: Luxury.GoldBase
    val initials = avatar?.initials ?: Avatars.initials(name)
    val triangle = avatar?.shape == AvatarShape.TRIANGLE
    val fontScale = when (avatar?.shape) {
        AvatarShape.TRIANGLE, AvatarShape.DIAMOND -> 0.28f
        AvatarShape.STAR -> 0.25f
        else -> 0.36f
    }
    Box(
        modifier = modifier.size(size).clip(shape).background(Luxury.BorderBrush).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(size * 0.06f).clip(shape).background(color),
            contentAlignment = if (triangle) Alignment.BottomCenter else Alignment.Center,
        ) {
            Text(
                initials,
                modifier = if (triangle) Modifier.padding(bottom = size * 0.12f) else Modifier,
                color = Luxury.CardBottom,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * fontScale).sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

private fun AvatarShape.toShape(): Shape = when (this) {
    AvatarShape.CIRCLE -> CircleShape
    AvatarShape.SQUARE -> RoundedCornerShape(22)
    AvatarShape.TRIANGLE -> polygon(listOf(0.5f to 0.02f, 0.98f to 0.94f, 0.02f to 0.94f))
    AvatarShape.DIAMOND -> polygon(listOf(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f))
    AvatarShape.HEXAGON -> polygon(listOf(0.25f to 0.03f, 0.75f to 0.03f, 1f to 0.5f, 0.75f to 0.97f, 0.25f to 0.97f, 0f to 0.5f))
    AvatarShape.STAR -> polygon(
        List(10) { i ->
            val radius = if (i % 2 == 0) 0.5f else 0.21f
            val angle = Math.toRadians(-90.0 + i * 36.0)
            (0.5f + radius * cos(angle).toFloat()) to (0.53f + radius * sin(angle).toFloat())
        },
    )
}

/** A shape from corner points given as fractions of the width and height. */
private fun polygon(points: List<Pair<Float, Float>>) = GenericShape { size, _ ->
    points.forEachIndexed { i, (x, y) ->
        if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
    }
    close()
}

private fun parseHex(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
