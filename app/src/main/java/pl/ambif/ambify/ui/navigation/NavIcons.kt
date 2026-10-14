package pl.ambif.ambify.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Line icons of the navigation bar modules, drawn in LocalContentColor.

@Composable
internal fun CalendarNavIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val left = size.width * 0.17f
        val top = size.height * 0.2f
        val iconWidth = size.width * 0.66f
        val iconHeight = size.height * 0.68f

        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(iconWidth, iconHeight),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawLine(
            color = color,
            start = Offset(left, top + iconHeight * 0.3f),
            end = Offset(left + iconWidth, top + iconHeight * 0.3f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(left + iconWidth * 0.27f, top - 1.dp.toPx()),
            end = Offset(left + iconWidth * 0.27f, top + 4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(left + iconWidth * 0.73f, top - 1.dp.toPx()),
            end = Offset(left + iconWidth * 0.73f, top + 4.dp.toPx()),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
internal fun AccountNavIcon() {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.16f,
            center = Offset(size.width / 2f, size.height * 0.32f),
            style = Stroke(width = strokeWidth),
        )
        val shoulders = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.88f)
            cubicTo(
                size.width * 0.2f,
                size.height * 0.61f,
                size.width * 0.8f,
                size.height * 0.61f,
                size.width * 0.82f,
                size.height * 0.88f,
            )
        }
        drawPath(
            path = shoulders,
            color = color,
            style = Stroke(width = strokeWidth),
        )
    }
}

/** Draws stroked paths given in unit coordinates (0..1 of the icon size). */
private class LineIconPainter(
    private val scope: DrawScope,
    private val color: Color,
    private val stroke: Stroke,
) {
    fun line(build: Path.() -> Unit) {
        val path = Path().apply(build)
        path.transform(Matrix().apply { scale(scope.size.width, scope.size.height) })
        scope.drawPath(path = path, color = color, style = stroke)
    }
}

/** Line icon in the style of the Calendar/Account icons. */
@Composable
private fun LineNavIcon(draw: LineIconPainter.() -> Unit) {
    val color = androidx.compose.material3.LocalContentColor.current
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        LineIconPainter(this, color, stroke).draw()
    }
}

@Composable
internal fun MissionsNavIcon() = LineNavIcon {
    // Flag on a pole.
    line { moveTo(0.26f, 0.9f); lineTo(0.26f, 0.12f) }
    line {
        moveTo(0.26f, 0.16f); lineTo(0.82f, 0.16f); lineTo(0.68f, 0.35f)
        lineTo(0.82f, 0.54f); lineTo(0.26f, 0.54f)
    }
}

@Composable
internal fun ShoppingNavIcon() = LineNavIcon {
    // Shopping cart.
    line {
        moveTo(0.08f, 0.2f); lineTo(0.22f, 0.2f); lineTo(0.33f, 0.64f)
        lineTo(0.8f, 0.64f); lineTo(0.9f, 0.32f); lineTo(0.26f, 0.32f)
    }
    line { addOval(androidx.compose.ui.geometry.Rect(0.33f, 0.76f, 0.45f, 0.88f)) }
    line { addOval(androidx.compose.ui.geometry.Rect(0.68f, 0.76f, 0.8f, 0.88f)) }
}

@Composable
internal fun CommunicationNavIcon() = LineNavIcon {
    // Speech bubble with a tail.
    line {
        moveTo(0.24f, 0.16f); lineTo(0.76f, 0.16f)
        quadraticTo(0.9f, 0.16f, 0.9f, 0.3f); lineTo(0.9f, 0.56f)
        quadraticTo(0.9f, 0.7f, 0.76f, 0.7f); lineTo(0.46f, 0.7f)
        lineTo(0.26f, 0.88f); lineTo(0.28f, 0.7f); lineTo(0.24f, 0.7f)
        quadraticTo(0.1f, 0.7f, 0.1f, 0.56f); lineTo(0.1f, 0.3f)
        quadraticTo(0.1f, 0.16f, 0.24f, 0.16f); close()
    }
}

@Composable
internal fun TimetableNavIcon() = LineNavIcon {
    // Timetable grid: a header row and a column of lesson numbers.
    line { addRoundRect(androidx.compose.ui.geometry.RoundRect(0.14f, 0.16f, 0.86f, 0.86f, 0.1f, 0.1f)) }
    line { moveTo(0.14f, 0.38f); lineTo(0.86f, 0.38f) }
    line { moveTo(0.14f, 0.62f); lineTo(0.86f, 0.62f) }
    line { moveTo(0.38f, 0.16f); lineTo(0.38f, 0.86f) }
}

@Composable
internal fun SchoolNavIcon() = LineNavIcon {
    // Graduation cap with a tassel.
    line {
        moveTo(0.5f, 0.2f); lineTo(0.94f, 0.4f); lineTo(0.5f, 0.6f); lineTo(0.06f, 0.4f); close()
    }
    line {
        moveTo(0.26f, 0.51f); lineTo(0.26f, 0.72f)
        quadraticTo(0.5f, 0.88f, 0.74f, 0.72f); lineTo(0.74f, 0.51f)
    }
    line { moveTo(0.94f, 0.4f); lineTo(0.94f, 0.66f) }
}
