package pl.ambif.ambify.features.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.ui.theme.Luxury

// --- 0.7.8 weather looks (canvas boards 2, 7, 7b) ---

private val SunYellow = Color(0xFFFFC83D)
private val SunRay = Color(0xFFFFB020)
private val CloudLight = Color(0xFFDCE3EA)
private val CloudGrey = Color(0xFFAEB8C4)
private val CloudDark = Color(0xFF7E8898)
private val RainBlue = Color(0xFF4FA3FF)
private val Bolt = Color(0xFFFFD23F)
private val Moon = Color(0xFFE8E1B8)

/** What the icon shows (WMO code → picture); [night]: a clear sky is the moon. */
private enum class Sky { SUN, MOON, PARTLY, CLOUD, RAIN, SNOW, STORM, FOG }

private fun skyOf(code: Int, night: Boolean): Sky = when (code) {
    0, 1 -> if (night) Sky.MOON else Sky.SUN
    2 -> Sky.PARTLY
    3 -> Sky.CLOUD
    45, 48 -> Sky.FOG
    in 51..67, in 80..82 -> Sky.RAIN
    in 71..77, 85, 86 -> Sky.SNOW
    in 95..99 -> Sky.STORM
    else -> Sky.CLOUD
}

/** A colourful weather icon, the same in every theme (sun always yellow, rain blue). */
@Composable
fun WeatherIcon(code: Int, size: Dp, night: Boolean = false, modifier: Modifier = Modifier) {
    val sky = skyOf(code, night)
    Canvas(modifier.size(size).semantics { contentDescription = weatherWords(code) }) {
        val u = this.size.minDimension / 48f
        when (sky) {
            Sky.SUN -> sun(Offset(24f * u, 24f * u), 9.5f * u, u, rays = true)
            Sky.MOON -> {
                val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(8f * u, 8f * u), Size(32f * u, 32f * u))) }
                val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(18f * u, 4f * u), Size(26f * u, 26f * u))) }
                drawPath(Path().apply { op(outer, bite, androidx.compose.ui.graphics.PathOperation.Difference) }, Moon)
            }
            Sky.PARTLY -> {
                sun(Offset(17f * u, 16f * u), 7.5f * u, u, rays = true)
                cloud(u, CloudLight, top = 22f)
            }
            Sky.CLOUD -> cloud(u, Color(0xFFC9D2DC), top = 16f)
            Sky.RAIN -> {
                cloud(u, CloudGrey, top = 10f)
                listOf(16f, 24f, 32f).forEach { x ->
                    drawLine(RainBlue, Offset(x * u, 34f * u), Offset((x - 2f) * u, 40f * u), 3f * u, StrokeCap.Round)
                }
            }
            Sky.SNOW -> {
                cloud(u, Color(0xFFB8C4D0), top = 10f)
                listOf(Offset(15f, 37f), Offset(24f, 41f), Offset(33f, 37f)).forEach { drawCircle(Color.White, 2.4f * u, Offset(it.x * u, it.y * u)) }
            }
            Sky.STORM -> {
                cloud(u, CloudDark, top = 10f)
                val bolt = Path().apply {
                    moveTo(25f * u, 27f * u); lineTo(19f * u, 37f * u); lineTo(24f * u, 37f * u)
                    lineTo(21f * u, 45f * u); lineTo(30f * u, 33f * u); lineTo(25f * u, 33f * u); lineTo(28f * u, 27f * u); close()
                }
                drawPath(bolt, Bolt)
            }
            Sky.FOG -> listOf(18f to 8f, 25f to 12f, 32f to 8f, 39f to 14f).forEach { (y, x) ->
                drawLine(Color(0xFFB8C2CC), Offset(x * u, y * u), Offset((x + 26f) * u, y * u), 3.2f * u, StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, r: Float, u: Float, rays: Boolean) {
    if (rays) for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
        drawLine(SunRay, Offset(center.x + c * (r + 4f * u), center.y + s * (r + 4f * u)), Offset(center.x + c * (r + 8.5f * u), center.y + s * (r + 8.5f * u)), 2.8f * u, StrokeCap.Round)
    }
    drawCircle(SunYellow, r, center)
}

/** A cloud whose bottom is at [top] + 18 (in 48-units). */
private fun DrawScope.cloud(u: Float, color: Color, top: Float) {
    val b = top + 18f
    drawCircle(color, 7.5f * u, Offset(15f * u, (b - 7f) * u))
    drawCircle(color, 10.5f * u, Offset(25f * u, (b - 10f) * u))
    drawCircle(color, 8f * u, Offset(34f * u, (b - 7.5f) * u))
    drawRoundRect(color, Offset(8f * u, (b - 8f) * u), Size(34f * u, 8f * u), androidx.compose.ui.geometry.CornerRadius(4f * u))
}

/**
 * The colour of a temperature (board 7): lodowato (dark navy, with an icy glow on dark themes), mróz, chłodno,
 * przyjemnie (the text colour), ciepło, gorąco, upał. Light themes get darker shades so they stay readable.
 */
fun tempColor(t: Double, light: Boolean = Luxury.palette.light): Color = when {
    t <= -10 -> Color(0xFF1F3290)
    t < 0 -> if (light) Color(0xFF1E6FC2) else Color(0xFF6FB7FF)
    t < 10 -> if (light) Color(0xFF1F7F76) else Color(0xFF9ED7D0)
    t < 20 -> Luxury.Text
    t < 27 -> if (light) Color(0xFFA77800) else Color(0xFFFFD25A)
    t < 32 -> if (light) Color(0xFFC25E00) else Color(0xFFFF9B3D)
    else -> if (light) Color(0xFFC0281A) else Color(0xFFFF5A3C)
}

/** A temperature in its colour; ≤ −10° on a dark theme gets a thin icy glow, else the navy disappears (board 7b B). */
@Composable
fun Temp(value: Double, fontSize: TextUnit, fontWeight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) {
    val light = Luxury.palette.light
    val glow = value <= -10 && !light
    Text(
        deg(value), fontSize = fontSize, fontWeight = fontWeight, color = tempColor(value, light), modifier = modifier,
        maxLines = 1, softWrap = false,
        style = if (glow) TextStyle(shadow = Shadow(Color(0xFFE6F6FF), Offset.Zero, 8f)) else TextStyle.Default,
    )
}

/** Effects only at the extremes, and off with the cards' animations: frost in the corners at ≤ −10°. */
@Composable
fun FrostOverlay(modifier: Modifier = Modifier) {
    val animations by LocalAppSession.current.device.cardAnimations.collectAsState()
    val shimmer = if (animations) {
        val t = rememberInfiniteTransition(label = "frost")
        t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "frost").value
    } else 0.85f
    Canvas(modifier) {
        val ice = Color(0xFFDCF5FF)
        drawRect(Brush.radialGradient(listOf(ice.copy(alpha = 0.30f * shimmer), Color.Transparent), Offset.Zero, 80.dp.toPx()))
        drawRect(Brush.radialGradient(listOf(ice.copy(alpha = 0.24f * shimmer), Color.Transparent), Offset(size.width, size.height), 90.dp.toPx()))
        drawRect(Brush.radialGradient(listOf(ice.copy(alpha = 0.16f * shimmer), Color.Transparent), Offset(size.width, 0f), 56.dp.toPx()))
        listOf(Offset(14f, 12f), Offset(30f, 26f), Offset(size.width / density - 18f, size.height / density - 16f)).forEach { o ->
            val c = Offset(o.x.dp.toPx(), o.y.dp.toPx())
            val r = 5.dp.toPx()
            for (i in 0 until 3) {
                val a = Math.toRadians(i * 60.0)
                val dx = (Math.cos(a) * r).toFloat(); val dy = (Math.sin(a) * r).toFloat()
                drawLine(ice.copy(alpha = 0.7f * shimmer), Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), 1.2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** Heat haze over the number at ≥ 32°: three warm waves rising. */
@Composable
fun HeatHaze(modifier: Modifier = Modifier) {
    val animations by LocalAppSession.current.device.cardAnimations.collectAsState()
    val phase = if (animations) {
        val t = rememberInfiniteTransition(label = "heat")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "heat").value
    } else 0.4f
    Canvas(modifier) {
        listOf(0.2f, 0.5f, 0.8f).forEachIndexed { i, x ->
            val p = (phase + i / 3f) % 1f
            val alpha = if (p < 0.4f) p / 0.4f else 1f - (p - 0.4f) / 0.6f
            val path = Path()
            val cx = size.width * x
            val bottom = size.height - p * size.height * 0.5f
            path.moveTo(cx, bottom)
            path.cubicTo(cx - 6.dp.toPx(), bottom - 6.dp.toPx(), cx + 6.dp.toPx(), bottom - 12.dp.toPx(), cx, bottom - 18.dp.toPx())
            drawPath(path, (if (i == 1) Color(0xFFFFD25A) else Color(0xFFFF9B3D)).copy(alpha = 0.7f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
