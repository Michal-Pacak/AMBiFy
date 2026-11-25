package pl.ambif.ambify.features.lists

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.features.lists.data.SyncStatus
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.ItemFile
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.PopupButtons
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

// 0.7.3, boards 25–26: photos and files of items (premium "Linki, zdjęcia i pliki"), kept on the server, downloaded
// only when someone taps them. Three different marks: ogniwo = link, aparat = zdjęcie, kartka = plik.

/** The server's photos of the item and the ones still waiting on this phone (as files of mine). */
private fun photosOf(ui: ListsUi, lists: ListsController, item: ListItem): List<ItemFile> =
    item.files.filter { it.photo } + lists.files.pendingOf(item.id).filter { it.photo }.map { it.asItemFile(ui.me.id) }

private fun filesOf(ui: ListsUi, lists: ListsController, item: ListItem): List<ItemFile> =
    item.files.filterNot { it.photo } + lists.files.pendingOf(item.id).filterNot { it.photo }.map { it.asItemFile(ui.me.id) }

/**
 * The line of marks under an item's name (board 25): ogniwo + domena, aparat + liczba, kartka + nazwa (or the number of
 * files), and "czeka na wysłanie" / "wysyłanie…" while something of it is still on this phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AttachmentsLine(ui: ListsUi, lists: ListsController, item: ListItem, onPhotos: () -> Unit, onFile: (ItemFile) -> Unit, onFiles: () -> Unit) {
    val pendingAll by lists.files.pending.collectAsState()
    val sending by lists.files.sending.collectAsState()
    val pending = pendingAll.filter { it.itemId == item.id }
    val links = item.values.links
    val photos = item.files.count { it.photo } + pending.count { it.photo }
    val files = filesOf(ui, lists, item)
    if (links.isEmpty() && photos == 0 && files.isEmpty()) return
    val uri = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        if (links.isNotEmpty()) {
            val first = links.first()
            Mark(AppIcons.Link, first.title.ifBlank { Lists.domain(first.url) } + if (links.size > 1) "  +${links.size - 1}" else "", "Otwórz link w przeglądarce") {
                runCatching { uri.openUri(first.url) }
            }
        }
        if (photos > 0) Mark(AppIcons.Camera, "$photos", "Zdjęcia: $photos, otwórz", onPhotos)
        when (files.size) {
            0 -> Unit
            1 -> Mark(AppIcons.Document, files.single().name, "Otwórz plik ${files.single().name}") { onFile(files.single()) }
            else -> Mark(AppIcons.Document, "${files.size}", "Pliki: ${files.size}, pokaż", onFiles)
        }
        if (pending.isNotEmpty()) {
            val now = pending.any { it.id == sending?.first }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(min = 28.dp)) {
                Icon(if (now) AppIcons.Upload else AppIcons.Clock, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(12.dp))
                Text(if (now) "wysyłanie…" else "czeka na wysłanie", fontSize = 11.sp, color = Luxury.GoldText)
            }
        }
    }
}

@Composable
private fun Mark(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 30.dp).clip(RoundedCornerShape(15.dp)).background(Luxury.GoldBase.copy(alpha = 0.10f))
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 12.sp, color = Luxury.GoldText, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { contentDescription = description })
    }
}

/**
 * "Zdjęcia i pliki" in the item's window (board 25): what the item has, what waits or is being sent, "+ Zdjęcie"
 * (aparat / galeria) and "+ Plik". Added at once (not with "Zapisz"); the viewer and the file open over the window.
 */
@Composable
internal fun FilesEditor(ui: ListsUi, list: ListInfo, lists: ListsController, item: ListItem?) {
    LuxurySectionTitle("Zdjęcia i pliki")
    if (item == null) {
        Text("Zdjęcia i pliki dodasz po zapisaniu pozycji.", fontSize = 12.sp, color = Muted)
        return
    }
    val context = LocalContext.current
    val current = ui.data.item(item.id) ?: item
    val pendingAll by lists.files.pending.collectAsState()
    val sending by lists.files.sending.collectAsState()
    val pending = pendingAll.filter { it.itemId == item.id }
    val photos = photosOf(ui, lists, current)
    val files = filesOf(ui, lists, current)
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    var opening by remember { mutableStateOf<ItemFile?>(null) }
    var removing by remember { mutableStateOf<ItemFile?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val canAdd = ui.links && ui.canAddTo(list, current) && !current.pending
    val refused: (String) -> Unit = { error = it }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) lists.addPhotos(context, current, listOf(Uri.fromFile(file)), refused)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Lists.MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) lists.addPhotos(context, current, uris, refused)
    }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lists.addFile(context, current, uri, refused)
    }

    if (pending.isNotEmpty() && ui.status is SyncStatus.Offline) {
        MessageBar(MessageKind.STATUS, "Bez połączenia", "Nowe zdjęcia i pliki czekają w telefonie i wyślą się same, gdy wróci sieć.", icon = AppIcons.Offline)
    }
    if (photos.isNotEmpty()) {
        val waiting = pending.count { it.photo }
        AttachmentRow(
            AppIcons.Camera, "Zdjęcia · ${photos.size}",
            if (waiting > 0) "${photos.size - waiting} na serwerze · $waiting czeka na wysłanie" else "na serwerze — otwierają się po stuknięciu",
            action = "Otwórz ›", onClick = { viewer = 0 },
        )
    }
    sending?.let { (id, bytes) ->
        pending.firstOrNull { it.id == id }?.let { now ->
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Luxury.Surface).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(AppIcons.Upload, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(18.dp))
                    Text("Wysyłanie · ${Lists.sizeText(bytes)} z ${Lists.sizeText(now.size)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
                LinearProgressIndicator(
                    progress = { if (now.size > 0) (bytes.toFloat() / now.size).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Wysyłanie ${now.name}" },
                    color = Luxury.GoldBase, trackColor = Outline,
                )
            }
        }
    }
    files.forEach { file ->
        val waits = pending.any { it.id == file.id }
        val mayRemove = waits || ui.canDeleteFile(list, file)
        AttachmentRow(
            AppIcons.Document, file.name,
            if (waits) "czeka na wysłanie · ${Lists.sizeText(file.size)}" else "plik · ${Lists.sizeText(file.size)} · otwiera aplikacja telefonu",
            subtitleGold = waits,
            onClick = { opening = file },
            onRemove = if (mayRemove) ({ if (waits) lists.deleteFile(file, onError = refused) else removing = file }) else null,
            removeLabel = "Usuń plik ${file.name}",
        )
    }
    if (canAdd) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddButton("+ Zdjęcie", enabled = photos.size < Lists.MAX_PHOTOS, modifier = Modifier.weight(1f)) { error = null; picking = true }
            AddButton("+ Plik", enabled = files.size < Lists.MAX_FILES, modifier = Modifier.weight(1f)) { error = null; document.launch(arrayOf("*/*")) }
        }
        if (photos.size >= Lists.MAX_PHOTOS) {
            MessageBar(MessageKind.WARNING, "Limit zdjęć", "Pozycja ma już ${Lists.MAX_PHOTOS} zdjęć. Usuń któreś, żeby dodać nowe.")
        }
    } else if (photos.isEmpty() && files.isEmpty()) {
        Text("Brak zdjęć i plików.", fontSize = 12.sp, color = Muted)
    }
    error?.let { MessageBar(MessageKind.ERROR, "Nie dodano", it, onClose = { error = null }) }
    if (canAdd) {
        Text(
            "Do ${Lists.MAX_PHOTOS} zdjęć i ${Lists.MAX_FILES} plików na pozycję · plik do ${Lists.sizeText(Lists.MAX_FILE_BYTES)} · PDF, dokumenty, arkusze, tekst. " +
                "Usunąć może ten, kto dodał, i twórca listy.",
            fontSize = 11.sp, color = Faint,
        )
    }

    if (picking) {
        LuxuryPopup(onDismiss = { picking = false }) {
            Text("Dodaj zdjęcie", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            PickRow(AppIcons.Camera, "Zrób zdjęcie", "aparat telefonu") {
                picking = false
                val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
                cameraFile = file
                runCatching { camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file)) }
                    .onFailure { error = "Nie udało się włączyć aparatu." }
            }
            PickRow(AppIcons.Gallery, "Wybierz z galerii", "można kilka naraz (razem do ${Lists.MAX_PHOTOS})") {
                picking = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            MessageBar(MessageKind.INFO, "Jakość", "Wysyłamy w dobrej jakości (do ${Lists.PHOTO_SIDE} px, zwykle 1–2 MB), bez lokalizacji zapisanej przez aparat.")
            PopupButtons {
                FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth()) { picking = false }
            }
        }
    }
    viewer?.let { start -> PhotoViewer(ui, list, lists, item.id, start) { viewer = null } }
    opening?.let { file -> FileOpener(ui, lists, file) { opening = null } }
    removing?.let { file ->
        LuxuryPopup(onDismiss = { removing = null }) {
            Text("Usunąć plik?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            Text("„${file.name}” zniknie u wszystkich i z serwera. Tego nie da się cofnąć.", fontSize = 14.sp, color = Muted)
            PopupButtons {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f)) { removing = null }
                    DangerButton("Usuń", { removing = null; lists.deleteFile(file, onError = refused) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleGold: Boolean = false,
    action: String? = null,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(14.dp)).background(Luxury.Surface)
            .clickable(onClickLabel = "Otwórz", role = Role.Button, onClick = onClick).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 11.sp, color = if (subtitleGold) Luxury.GoldText else Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        action?.let { Text(it, fontSize = 13.sp, color = Luxury.GoldText, modifier = Modifier.padding(end = 8.dp)) }
        if (onRemove != null) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = removeLabel, role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, contentDescription = removeLabel, tint = Faint, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun AddButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (enabled) Luxury.GoldBase.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (enabled) Luxury.GoldText else Faint, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont) }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = Muted)
        }
    }
}

// --- board 26: a photo full screen ---

private sealed interface PhotoState {
    data class Loading(val bytes: Long) : PhotoState
    data class Ready(val image: ImageBitmap) : PhotoState
    data object Offline : PhotoState
    data class Failed(val message: String) : PhotoState
}

/**
 * The item's photos full screen (board 26): przesuń w bok — następne, rozsuń palce lub stuknij dwa razy — powiększ.
 * Each one is downloaded when shown (once; then it opens at once, also offline).
 */
@Composable
internal fun PhotoViewer(ui: ListsUi, list: ListInfo, lists: ListsController, itemId: String, start: Int, onDismiss: () -> Unit) {
    val item = ui.data.item(itemId)
    val photos = item?.let { photosOf(ui, lists, it) }.orEmpty()
    if (item == null || photos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, photos.lastIndex)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ItemFile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                PhotoPage(lists, photos[page], item.values.name, onZoom = { if (page == pager.currentPage) zoomed = it })
            }
            // ‹ › only with more than one photo (Michał, 22.11): the same as a swipe, also for those who do not swipe.
            if (photos.size > 1 && !zoomed) {
                val scope = rememberCoroutineScope()
                if (pager.currentPage > 0) {
                    Box(modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                        GoldArrow(AppIcons.Back, "Poprzednie zdjęcie") { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
                    }
                }
                if (pager.currentPage < photos.lastIndex) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                        GoldArrow(AppIcons.Forward, "Następne zdjęcie") { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                    }
                }
            }
            val shown = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoundIcon(AppIcons.Close, "Zamknij podgląd", onDismiss)
                Column(modifier = Modifier.weight(1f)) {
                    Text("${item.values.name} · ${pager.currentPage + 1} z ${photos.size}", fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val waits = lists.files.pendingOf(item.id).any { it.id == shown.id }
                    Text(
                        "${ui.name(shown.addedBy)} · ${time(shown.addedAt)} · ${Lists.sizeText(shown.size)}" + if (waits) " · czeka na wysłanie" else "",
                        fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (lists.files.pendingOf(item.id).any { it.id == shown.id } || ui.canDeleteFile(list, shown)) {
                    RoundIcon(AppIcons.Trash, "Usuń to zdjęcie") { removing = shown }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { MessageBar(MessageKind.ERROR, "Nie usunięto", it, onClose = { error = null }) }
                if (photos.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.indices.forEach { i ->
                            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == pager.currentPage) Luxury.GoldText else Luxury.Faint))
                        }
                    }
                }
                Text(
                    if (photos.size > 1) "Strzałka lub przesunięcie w bok: następne zdjęcie · rozsuń palce: powiększ" else "Rozsuń palce lub stuknij dwa razy: powiększ",
                    fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center,
                )
            }
        }
        removing?.let { photo ->
            LuxuryPopup(onDismiss = { removing = null }) {
                Text("Usunąć zdjęcie?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
                Text("Zniknie u wszystkich i z serwera. Tego nie da się cofnąć.", fontSize = 14.sp, color = Muted)
                PopupButtons {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f)) { removing = null }
                        DangerButton("Usuń", {
                            removing = null
                            lists.deleteFile(photo, onDone = { if (photos.size <= 1) onDismiss() }, onError = { error = it })
                        }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPage(lists: ListsController, photo: ItemFile, itemName: String, onZoom: (Boolean) -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(photo.id) { mutableStateOf<PhotoState>(PhotoState.Loading(0)) }
    LaunchedEffect(photo.id, attempt) {
        state = PhotoState.Loading(0)
        state = try {
            val file = lists.files.open(photo) { state = PhotoState.Loading(it) }
            PhotoState.Ready(decodeForScreen(context, file))
        } catch (e: ServerError) {
            if (e.code == "network") PhotoState.Offline else PhotoState.Failed(e.message)
        } catch (e: Exception) {
            PhotoState.Failed("Nie udało się pokazać tego zdjęcia.")
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val now = state) {
            is PhotoState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = Luxury.GoldText, trackColor = Luxury.GoldBase.copy(alpha = 0.2f))
                Text("Pobieranie zdjęcia… ${Lists.sizeText(now.bytes)} z ${Lists.sizeText(photo.size)}", fontSize = 14.sp, color = Luxury.TextMuted)
                Text("Następnym razem otworzy się od razu.", fontSize = 12.sp, color = Muted)
            }
            is PhotoState.Ready -> ZoomableImage(now.image, "Zdjęcie: $itemName", onZoom)
            PhotoState.Offline -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MessageBar(MessageKind.STATUS, "Bez połączenia", "To zdjęcie otworzysz, gdy wróci sieć. Zdjęcia oglądane wcześniej otwierają się też bez sieci.", icon = AppIcons.Offline)
                FramedButton("Spróbuj ponownie", modifier = Modifier.fillMaxWidth()) { attempt++ }
            }
            is PhotoState.Failed -> Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MessageBar(MessageKind.ERROR, "Nie otwarto zdjęcia", now.message)
                FramedButton("Spróbuj ponownie", modifier = Modifier.fillMaxWidth()) { attempt++ }
            }
        }
    }
}

@Composable
private fun ZoomableImage(image: ImageBitmap, description: String, onZoom: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun set(newScale: Float, newOffset: Offset) {
        scale = newScale.coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else newOffset
        onZoom(scale > 1f)
    }
    Image(
        bitmap = image,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap -> if (scale > 1f) set(1f, Offset.Zero) else set(2.5f, (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f) })
            }
            .pointerInput(Unit) {
                // Only two fingers, or a photo already zoomed in, are ours: one finger on a whole photo goes to the
                // pager, so a swipe moves to the next photo (detectTransformGestures would take every swipe).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                set(scale * zoom, offset + pan)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

@Composable
private fun GoldArrow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Luxury.GoldBase, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Luxury.GoldText, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Luxury.Text, modifier = Modifier.size(22.dp)) }
}

/** Small enough for the screen (the full photo stays in the file for zooming in another app). */
private suspend fun decodeForScreen(context: Context, file: File): ImageBitmap = withContext(Dispatchers.IO) {
    val longest = maxOf(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels) * 2
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val (w, h) = info.size.width to info.size.height
        val longer = maxOf(w, h)
        if (longer > longest) decoder.setTargetSize(w * longest / longer, h * longest / longer)
    }.asImageBitmap()
}

// --- board 26: a file, opened by an app of the phone ---

private sealed interface FileState {
    data class Loading(val bytes: Long) : FileState
    data object Offline : FileState
    data class NoApp(val file: File) : FileState
    data class Failed(val message: String) : FileState
    data object Saved : FileState
}

/** Downloads the file (once) and gives it to an app of the phone; without one it can be saved in Pobrane. */
@Composable
internal fun FileOpener(ui: ListsUi, lists: ListsController, file: ItemFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<FileState>(FileState.Loading(0)) }
    LaunchedEffect(attempt) {
        state = FileState.Loading(0)
        try {
            val local = lists.files.open(file) { state = FileState.Loading(it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", local)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.resolveActivity(context.packageManager) == null) {
                state = FileState.NoApp(local)
            } else try {
                context.startActivity(intent)
                onDismiss()
            } catch (_: ActivityNotFoundException) {
                state = FileState.NoApp(local)
            }
        } catch (e: ServerError) {
            state = if (e.code == "network") FileState.Offline else FileState.Failed(e.message)
        } catch (e: Exception) {
            state = FileState.Failed("Nie udało się otworzyć tego pliku.")
        }
    }
    LuxuryPopup(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AppIcons.Document, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(30.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${ui.name(file.addedBy)} · ${time(file.addedAt)} · ${Lists.sizeText(file.size)}", fontSize = 12.sp, color = Muted)
            }
        }
        when (val now = state) {
            is FileState.Loading -> {
                Text("Pobieranie… ${Lists.sizeText(now.bytes)} z ${Lists.sizeText(file.size)}", fontSize = 13.sp, color = Luxury.TextMuted)
                LinearProgressIndicator(
                    progress = { if (file.size > 0) (now.bytes.toFloat() / file.size).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(), color = Luxury.GoldBase, trackColor = Outline,
                )
                Text("Potem otworzy go aplikacja z telefonu (np. podgląd PDF).", fontSize = 12.sp, color = Muted)
                PopupButtons {
                    FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
            FileState.Offline -> {
                MessageBar(MessageKind.STATUS, "Bez połączenia", "Ten plik otworzysz, gdy wróci sieć. Pliki otwierane wcześniej otwierają się też bez sieci.", icon = AppIcons.Offline)
                PopupButtons {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FramedButton("Zamknij", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                        FramedButton("Spróbuj ponownie", modifier = Modifier.weight(1f)) { attempt++ }
                    }
                }
            }
            is FileState.NoApp -> {
                val ending = file.name.substringAfterLast('.', "").ifEmpty { "tego typu" }
                MessageBar(
                    MessageKind.ERROR, "Brak aplikacji",
                    "Telefon nie ma aplikacji, która otwiera pliki .$ending. Zapisz plik i otwórz go na komputerze albo zainstaluj przeglądarkę takich plików.",
                )
                PopupButtons {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FramedButton("Zamknij", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                        Box(modifier = Modifier.weight(1f)) {
                            GoldButton("Zapisz w Pobranych") {
                                scope.launch {
                                    state = if (saveToDownloads(context, now.file, file)) FileState.Saved else FileState.Failed("Nie udało się zapisać pliku w Pobranych.")
                                }
                            }
                        }
                    }
                }
            }
            is FileState.Failed -> {
                MessageBar(MessageKind.ERROR, "Nie otwarto pliku", now.message)
                PopupButtons {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FramedButton("Zamknij", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                        FramedButton("Spróbuj ponownie", modifier = Modifier.weight(1f)) { attempt++ }
                    }
                }
            }
            FileState.Saved -> {
                MessageBar(MessageKind.SUCCESS, "Zapisano w Pobranych", "„${file.name}” jest w folderze Pobrane telefonu.")
                PopupButtons {
                    FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
                }
            }
        }
    }
}

private suspend fun saveToDownloads(context: Context, source: File, file: ItemFile): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, file.mime)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        true
    }.getOrDefault(false)
}
