package pl.ambif.ambify.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.shared.Wallpaper

/** Wallpaper photos: kept on the phone ([init] gives the folder and the server), so they show offline too. */
object WallpaperImages {
    private var dir: File? = null
    private var api: ServerApi? = null

    fun init(context: Context, api: ServerApi) {
        dir = context.filesDir.resolve("wallpapers").apply { mkdirs() }
        this.api = api
    }

    fun file(photoId: String): File? = dir?.resolve("$photoId.jpg")

    /** The photo, from the phone or downloaded once. */
    suspend fun load(photoId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val target = file(photoId) ?: return@withContext null
        if (!target.isFile) {
            val part = File(target.path + ".part")
            runCatching { api?.downloadWallpaper(photoId, part) }.getOrNull() ?: return@withContext null
            part.renameTo(target)
        }
        // Not bigger than the screen needs: a quarter of the pixels of a 2048 px photo is plenty.
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        BitmapFactory.decodeFile(target.path, options)?.asImageBitmap()
    }

    /** A photo just picked in the editor, kept under its new id without downloading it again. */
    fun keep(photoId: String, source: File) {
        file(photoId)?.let { source.copyTo(it, overwrite = true) }
    }
}

/** The own theme's wallpaper behind everything (patterns in its colors, or a photo), dimmed by its background. */
@Composable
fun WallpaperLayer(modifier: Modifier = Modifier) {
    val palette = Luxury.palette
    val wallpaper = palette.wallpaper ?: return
    WallpaperView(wallpaper, palette, modifier)
}

@Composable
fun WallpaperView(wallpaper: Wallpaper, palette: Palette, modifier: Modifier = Modifier) {
    val dim = palette.background.copy(alpha = wallpaper.dim / 100f)
    Box(modifier = modifier.fillMaxSize()) {
        val photo = wallpaper.photoId
        if (photo != null) {
            var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(photo) { bitmap = WallpaperImages.load(photo) }
            bitmap?.let {
                Image(
                    it, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = BiasAlignment(wallpaper.focusX * 2f - 1f, wallpaper.focusY * 2f - 1f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().drawBehind {
                if (photo == null) wallpaper.pattern?.let { drawPattern(it, palette.trio.ifEmpty { listOf(palette.accent, palette.accentButton, palette.accentText) }) }
                drawRect(dim)
            },
        )
    }
}

/** The nine patterns of the editor (canvas board 4), drawn in the theme's three colors. */
fun DrawScope.drawPattern(pattern: String, trio: List<Color>) {
    val a = trio[0]
    val b = trio.getOrElse(1) { a }
    val c = trio.getOrElse(2) { a }
    val w = size.width
    val h = size.height
    fun stripes(width: Float, horizontal: Boolean) {
        var i = 0
        var at = 0f
        val end = if (horizontal) h else w
        while (at < end) {
            val color = listOf(a, b, c)[i % 3]
            if (horizontal) drawRect(color, Offset(0f, at), Size(w, width)) else drawRect(color, Offset(at, 0f), Size(width, h))
            at += width
            i++
        }
    }
    when (pattern) {
        "scarf" -> stripes(46.dp.toPx(), horizontal = true)
        "vert" -> stripes(30.dp.toPx(), horizontal = false)
        "flag" -> {
            drawRect(a, Offset.Zero, Size(w, h / 3f))
            drawRect(b, Offset(0f, h / 3f), Size(w, h / 3f))
            drawRect(c, Offset(0f, 2 * h / 3f), Size(w, h - 2 * h / 3f))
        }
        "check" -> {
            drawRect(c)
            val s = 28.dp.toPx()
            var y = 0
            while (y * s < h) {
                var x = 0
                while (x * s < w) {
                    drawRect(if ((x + y) % 2 == 0) a else b, Offset(x * s, y * s), Size(s, s))
                    x++
                }
                y++
            }
        }
        "rhomb" -> {
            drawRect(b)
            val s = 34.dp.toPx()
            rotate(45f) {
                var y = -h
                var row = 0
                while (y < 2 * h) {
                    var x = -w
                    var col = 0
                    while (x < 2 * w) {
                        if ((row + col) % 2 == 0) drawRect(if (row % 2 == 0) a else c, Offset(x, y), Size(s, s))
                        x += s
                        col++
                    }
                    y += s
                    row++
                }
            }
        }
        "dots" -> {
            drawRect(b)
            val s = 40.dp.toPx()
            var y = 0f
            while (y < h + s) {
                var x = 0f
                while (x < w + s) {
                    drawCircle(a, 8.dp.toPx(), Offset(x, y))
                    drawCircle(c, 5.dp.toPx(), Offset(x + s / 2, y + s / 2))
                    x += s
                }
                y += s
            }
        }
        "zigzag" -> {
            drawRect(b)
            val step = 24.dp.toPx()
            var y = 0f
            var row = 0
            while (y < h + step) {
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    var up = true
                    while (x < w + step) {
                        x += step
                        lineTo(x, if (up) y - step / 2 else y)
                        up = !up
                    }
                }
                drawPath(path, if (row % 2 == 0) a else c, style = Stroke(width = 8.dp.toPx()))
                y += step * 1.5f
                row++
            }
        }
        "tartan" -> {
            drawRect(b)
            val s = 46.dp.toPx()
            val band = 10.dp.toPx()
            var y = s - band
            while (y < h) { drawRect(a, Offset(0f, y), Size(w, band)); y += s }
            var x = s - band
            while (x < w) { drawRect(c.copy(alpha = 0.8f), Offset(x, 0f), Size(band, h)); x += s }
        }
        else -> rotate(45f) {
            // "diag": diagonal stripes; drawn rotated over a square big enough to cover the screen.
            val d = kotlin.math.hypot(w, h)
            val s = 34.dp.toPx()
            var at = -d
            var i = 0
            while (at < d * 2) {
                drawRect(listOf(a, b, c)[i % 3], Offset(at, -d), Size(s, d * 3))
                at += s
                i++
            }
        }
    }
}
