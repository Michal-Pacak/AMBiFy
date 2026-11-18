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

    /** Error: a circle with "!" (Komunikaty canvas). */
    val Alert: ImageVector by lazy { line("Alert") { circle(); moveTo(12f, 7.5f); lineTo(12f, 13f); moveTo(12f, 16.5f); lineTo(12f, 17f) } }

    /** Warning and conflict: a triangle with "!". */
    val Warning: ImageVector by lazy {
        line("Warning") {
            moveTo(12f, 3f); lineTo(22f, 21f); lineTo(2f, 21f); close()
            moveTo(12f, 10f); lineTo(12f, 15f); moveTo(12f, 18f); lineTo(12f, 18.5f)
        }
    }

    /** Success: a circle with a tick. */
    val Success: ImageVector by lazy { line("Success") { circle(); moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16f, 9.5f) } }

    /** Information: a circle with "i". */
    val Info: ImageVector by lazy { line("Info") { circle(); moveTo(12f, 11f); lineTo(12f, 16.5f); moveTo(12f, 7.5f); lineTo(12f, 8f) } }

    /** No connection: crossed-out Wi-Fi. */
    val Offline: ImageVector by lazy {
        line("Offline") {
            moveTo(2f, 8.5f); arcTo(15f, 15f, 0f, false, true, 22f, 8.5f)
            moveTo(5.5f, 12f); arcTo(10f, 10f, 0f, false, true, 18.5f, 12f)
            moveTo(9f, 15.5f); arcTo(5f, 5f, 0f, false, true, 15f, 15.5f)
            moveTo(12f, 19f); lineTo(12.01f, 19f)
            moveTo(3f, 3f); lineTo(21f, 21f)
        }
    }

    /** Waiting: a clock. */
    val Clock: ImageVector by lazy { line("Clock") { circle(); moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f) } }

    val Cart: ImageVector by lazy {
        line("Cart") {
            moveTo(3f, 4f); lineTo(5f, 4f); lineTo(7.4f, 15f); lineTo(18.4f, 15f); lineTo(20.4f, 7f); lineTo(6.2f, 7f)
            moveTo(10.4f, 19.5f); arcTo(1.4f, 1.4f, 0f, true, true, 7.6f, 19.5f); arcTo(1.4f, 1.4f, 0f, true, true, 10.4f, 19.5f)
            moveTo(18.4f, 19.5f); arcTo(1.4f, 1.4f, 0f, true, true, 15.6f, 19.5f); arcTo(1.4f, 1.4f, 0f, true, true, 18.4f, 19.5f)
        }
    }

    /** A ticked box (task lists). */
    val TaskBox: ImageVector by lazy {
        line("TaskBox") {
            moveTo(7f, 4f); lineTo(17f, 4f); arcTo(3f, 3f, 0f, false, true, 20f, 7f); lineTo(20f, 17f)
            arcTo(3f, 3f, 0f, false, true, 17f, 20f); lineTo(7f, 20f); arcTo(3f, 3f, 0f, false, true, 4f, 17f)
            lineTo(4f, 7f); arcTo(3f, 3f, 0f, false, true, 7f, 4f); close()
            moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f)
        }
    }

    val Calendar: ImageVector by lazy {
        line("Calendar") {
            moveTo(7f, 5f); lineTo(17f, 5f); arcTo(3f, 3f, 0f, false, true, 20f, 8f); lineTo(20f, 17f)
            arcTo(3f, 3f, 0f, false, true, 17f, 20f); lineTo(7f, 20f); arcTo(3f, 3f, 0f, false, true, 4f, 17f)
            lineTo(4f, 8f); arcTo(3f, 3f, 0f, false, true, 7f, 5f); close()
            moveTo(4f, 10f); lineTo(20f, 10f); moveTo(9f, 3f); lineTo(9f, 7f); moveTo(15f, 3f); lineTo(15f, 7f)
        }
    }

    val Bell: ImageVector by lazy {
        line("Bell") {
            moveTo(6f, 16f); lineTo(6f, 11f); arcTo(6f, 6f, 0f, false, true, 18f, 11f); lineTo(18f, 16f); lineTo(20f, 18f); lineTo(4f, 18f); close()
            moveTo(10f, 21f); lineTo(14f, 21f)
        }
    }

    val Star: ImageVector by lazy {
        line("Star") {
            moveTo(12f, 3f); lineTo(14.6f, 8.6f); lineTo(20.6f, 9.3f); lineTo(16.1f, 13.4f); lineTo(17.3f, 19.4f)
            lineTo(12f, 16.5f); lineTo(6.7f, 19.4f); lineTo(7.9f, 13.4f); lineTo(3.4f, 9.3f); lineTo(9.4f, 8.6f); close()
        }
    }

    val Minus: ImageVector by lazy { line("Minus") { moveTo(5f, 12f); lineTo(19f, 12f) } }

    /** A circle of radius 9 around the middle of the 24 × 24 icon. */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.circle() {
        moveTo(21f, 12f); arcTo(9f, 9f, 0f, true, true, 3f, 12f); arcTo(9f, 9f, 0f, true, true, 21f, 12f); close()
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
