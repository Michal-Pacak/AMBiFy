package pl.ambif.ambify.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small line icons on a 24-unit grid (the app has no Material icon library). Tinted by Icon(). */
object AppIcons {
    val Eye: ImageVector by lazy {
        line("Eye") {
            moveTo(2f, 12f)
            curveTo(4.5f, 7f, 8f, 5f, 12f, 5f)
            curveTo(16f, 5f, 19.5f, 7f, 22f, 12f)
            curveTo(19.5f, 17f, 16f, 19f, 12f, 19f)
            curveTo(8f, 19f, 4.5f, 17f, 2f, 12f)
            close()
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, true, true, 9f, 12f)
            arcTo(3f, 3f, 0f, true, true, 15f, 12f)
        }
    }

    val EyeOff: ImageVector by lazy {
        line("EyeOff") {
            moveTo(2f, 12f)
            curveTo(4.5f, 7f, 8f, 5f, 12f, 5f)
            curveTo(16f, 5f, 19.5f, 7f, 22f, 12f)
            curveTo(19.5f, 17f, 16f, 19f, 12f, 19f)
            curveTo(8f, 19f, 4.5f, 17f, 2f, 12f)
            close()
            moveTo(4f, 4f)
            lineTo(20f, 20f)
        }
    }

    val Back: ImageVector by lazy {
        line("Back") {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }

    val Forward: ImageVector by lazy {
        line("Forward") {
            moveTo(9f, 5f)
            lineTo(16f, 12f)
            lineTo(9f, 19f)
        }
    }

    val ExpandMore: ImageVector by lazy {
        line("ExpandMore") {
            moveTo(6f, 9f)
            lineTo(12f, 15f)
            lineTo(18f, 9f)
        }
    }

    val Check: ImageVector by lazy {
        line("Check") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }

    val Plus: ImageVector by lazy {
        line("Plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Close: ImageVector by lazy {
        line("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Up: ImageVector by lazy {
        line("Up") {
            moveTo(6f, 15f)
            lineTo(12f, 9f)
            lineTo(18f, 15f)
        }
    }

    /** Settings: a gear with six soft teeth around a hub (version B, approved 16.11.2026). */
    val Gear: ImageVector by lazy {
        line("Gear") {
            val teeth = 6
            val (outer, inner) = 10.4f to 7.7f
            val (top, base) = 0.24 to 0.37 // half-width of a tooth at its top and base, in radians
            fun x(radius: Float, angle: Double) = 12f + radius * kotlin.math.cos(angle).toFloat()
            fun y(radius: Float, angle: Double) = 12f + radius * kotlin.math.sin(angle).toFloat()
            for (i in 0 until teeth) {
                val angle = 2 * Math.PI * i / teeth - Math.PI / 2
                if (i == 0) moveTo(x(inner, angle - base), y(inner, angle - base)) else lineTo(x(inner, angle - base), y(inner, angle - base))
                lineTo(x(outer, angle - top), y(outer, angle - top))
                lineTo(x(outer, angle + top), y(outer, angle + top))
                lineTo(x(inner, angle + base), y(inner, angle + base))
                val next = 2 * Math.PI * (i + 1) / teeth - Math.PI / 2 - base
                arcTo(inner, inner, 0f, false, true, x(inner, next), y(inner, next))
            }
            close()
            moveTo(15.2f, 12f)
            arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f)
            arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f)
        }
    }

    val More: ImageVector by lazy {
        ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
            for (y in listOf(5f, 12f, 19f)) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(14f, y)
                    arcTo(2f, 2f, 0f, true, true, 10f, y)
                    arcTo(2f, 2f, 0f, true, true, 14f, y)
                }
            }
        }.build()
    }

    private fun line(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()
}
