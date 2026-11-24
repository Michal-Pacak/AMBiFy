package pl.ambif.ambify.features.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.AppSession
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.PhotoRules
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup

private val Danger = Color(0xFFE79A86)

/**
 * "Zdjęcie profilowe" in Konto (canvas "Zdjęcia profilowe", board 1 A): my photo, the same in all my
 * communities; take one, pick one, remove it. Says where it is not seen.
 */
@Composable
internal fun ProfilePhotoPart(me: Me, session: AppSession, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var cropping by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) cropping = Uri.fromFile(file)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropping = uri }

    fun save(jpeg: File?) {
        busy = true
        scope.launch {
            try {
                session.setMyPhoto(jpeg)
                onNotice(if (jpeg != null) "Zdjęcie profilowe zapisane." else "Zdjęcie profilowe usunięte.")
            } catch (e: ServerError) {
                onNotice(if (e.code == "network") "Zdjęcie zapiszesz, gdy wróci połączenie." else e.message)
            } finally {
                jpeg?.delete()
                busy = false
            }
        }
    }

    Text(
        if (me.photo != null) "Widzą je osoby ze wszystkich Twoich społeczności." else "Dodaj zdjęcie — zobaczą je osoby ze wszystkich Twoich społeczności.",
        fontSize = 12.sp, color = Luxury.TextMuted,
    )
    PhotoAction(AppIcons.Camera, "Zrób zdjęcie", enabled = !busy) {
        val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
        cameraFile = file
        runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
            .onFailure { onNotice("Nie udało się włączyć aparatu.") }
    }
    PhotoAction(AppIcons.Gallery, "Wybierz z galerii", enabled = !busy) {
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    if (me.photo != null) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                .border(1.dp, Danger.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .clickable(enabled = !busy, role = Role.Button) { save(null) },
            contentAlignment = Alignment.Center,
        ) { Text("Usuń zdjęcie", color = Danger, fontSize = 14.sp) }
        me.communities.filter { it.photosBlocked }.forEach {
            MessageBar(MessageKind.STATUS, "W społeczności „${it.name}” zdjęcia są wyłączone — tam widać Twoje inicjały.")
        }
        me.communities.filter { !it.photosBlocked && it.myPhotoHidden }.forEach {
            MessageBar(MessageKind.STATUS, "W społeczności „${it.name}” Twoje zdjęcie jest ukryte.")
        }
    }

    cropping?.let { uri ->
        PhotoCropDialog(uri, onDismiss = { cropping = null; cameraFile?.delete() }) { jpeg ->
            cropping = null
            cameraFile?.delete()
            save(jpeg)
        }
    }
}

@Composable
private fun PhotoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, color = Luxury.GoldText)
    }
}

/**
 * Board 1 B: the photo under a round window; move it with a finger, spread two fingers to enlarge. Saves the square
 * in the window as a JPEG (written again, so nothing the camera stored in the file goes along).
 */
@Composable
private fun PhotoCropDialog(uri: Uri, onDismiss: () -> Unit, onSave: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { runCatching { decode(context, uri) }.getOrNull() }
        failed = bitmap == null
    }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    val windowDp = 280.dp
    val window = with(LocalDensity.current) { windowDp.toPx() }

    LuxuryPopup(onDismiss = onDismiss) {
        Text("Ustaw zdjęcie", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        val source = bitmap
        when {
            failed -> MessageBar(MessageKind.ERROR, "Nie udało się odczytać tego zdjęcia.")
            source == null -> Text("Wczytywanie…", fontSize = 13.sp, color = Luxury.TextMuted)
            else -> {
                val image: ImageBitmap = remember(source) { source.asImageBitmap() }
                val base = window / minOf(source.width, source.height)
                fun clamp(o: Offset, k: Float): Offset {
                    val maxX = (source.width * base * k - window) / 2
                    val maxY = (source.height * base * k - window) / 2
                    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier.size(windowDp).clip(RoundedCornerShape(16.dp)).pointerInput(source) {
                            detectTransformGestures { _, pan, gesture, _ ->
                                val k = (zoom * gesture).coerceIn(1f, 5f)
                                zoom = k
                                offset = clamp(offset + pan, k)
                            }
                        },
                    ) {
                        val scale = base * zoom
                        val w = source.width * scale
                        val h = source.height * scale
                        drawImage(
                            image,
                            dstOffset = IntOffset(((size.width - w) / 2 + offset.x).roundToInt(), ((size.height - h) / 2 + offset.y).roundToInt()),
                            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        )
                        val circle = Rect(Offset(size.width / 2, size.height / 2), size.minDimension / 2 - 2)
                        val shade = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(Offset.Zero, size))
                            addOval(circle)
                        }
                        drawPath(shade, Color(0xB80A0A0C))
                        drawOval(Luxury.GoldBase, circle.topLeft, circle.size, style = Stroke(2.dp.toPx()))
                    }
                }
                Text("Przesuń palcem · rozsuń palce, żeby powiększyć", fontSize = 12.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                GoldButton("Zapisz") {
                    scope.launch {
                        val file = withContext(Dispatchers.Default) { crop(context, source, base * zoom, offset, window) }
                        onSave(file)
                    }
                }
            }
        }
        FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/** The picture turned the right way up and not bigger than needed for cropping. */
private fun decode(context: Context, uri: Uri): Bitmap {
    val source = if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val (w, h) = info.size.width to info.size.height
        val longer = maxOf(w, h)
        if (longer > MAX_DECODE) decoder.setTargetSize(w * MAX_DECODE / longer, h * MAX_DECODE / longer)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

/** The square under the window: [scale] screen px per picture px, [offset] how far the picture was moved. */
private fun crop(context: Context, source: Bitmap, scale: Float, offset: Offset, window: Float): File {
    val side = (window / scale).roundToInt().coerceIn(1, minOf(source.width, source.height))
    val left = ((source.width * scale / 2 - window / 2 - offset.x) / scale).roundToInt().coerceIn(0, source.width - side)
    val top = ((source.height * scale / 2 - window / 2 - offset.y) / scale).roundToInt().coerceIn(0, source.height - side)
    val square = Bitmap.createBitmap(source, left, top, side, side)
    val out = if (side > PhotoRules.SIDE) Bitmap.createScaledBitmap(square, PhotoRules.SIDE, PhotoRules.SIDE, true) else square
    val file = File(context.cacheDir, "profile-photo-${UUID.randomUUID()}.jpg")
    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    return file
}

private const val MAX_DECODE = 2048
