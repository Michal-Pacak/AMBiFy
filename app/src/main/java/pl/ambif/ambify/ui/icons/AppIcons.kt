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
