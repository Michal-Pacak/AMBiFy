package pl.ambif.ambify.features.reports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.settings.SettingsLayout
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.MyReport
import pl.ambif.ambify.shared.ReportRules
import pl.ambif.ambify.shared.ReportStatus
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageChip
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.help.Help
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** The app-wide bubble (a report sent: shown on the screen the report came from, board 2 → 3). */
val LocalAppBubbles = androidx.compose.runtime.staticCompositionLocalOf<pl.ambif.ambify.ui.components.Bubbles?> { null }

/**
 * "Zgłoś błąd" (board 2): a description, the screenshot taken when "?" was tapped (it can go), up to 3 pictures in
 * all, and what the phone adds by itself. Offline the report waits on the phone and goes out with the network.
 */
@Composable
fun ReportBugScreen(onBack: () -> Unit) {
    val session = LocalAppSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bubbles = LocalAppBubbles.current
    val me = (session.state.collectAsState().value as? SessionState.SignedIn)?.me
    val draft = remember { ReportDraft.take() }
    val screen = draft.first
    val shots = remember { mutableStateListOf<File>() }
    var text by rememberSaveable { mutableStateOf("") }
    var details by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        draft.second?.let { bitmap -> saveJpeg(context, bitmap)?.let { shots.add(it) } }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { picture(context, uri)?.let { if (shots.size < ReportRules.MAX_SHOTS) shots.add(it) } ?: run { error = "Nie udało się wczytać tego zdjęcia." } }
    }

    fun send() {
        if (text.isBlank() || sending) return
        sending = true
        scope.launch {
            val report = newReport(context, text, screen, shots.size)
            val outbox = ReportOutbox.of(context)
            withContext(Dispatchers.IO) { outbox.add(report, shots.toList()) }
            val sent = outbox.send(session.api)
            if (sent) {
                bubbles?.show(MessageKind.SUCCESS, "Dziękujemy! Zgłoszenie trafiło do zespołu AMBiFy.")
                ReportsStore.refresh(context, session.api)
            } else {
                ReportsSyncWorker.sendWhenOnline(context)
                ReportsStore.waitingChanged(context)
                bubbles?.show(MessageKind.STATUS, "Brak połączenia", "Zgłoszenie wyślemy samo, gdy wróci sieć.")
            }
            sending = false
            onBack()
        }
    }

    // B-015: "Anuluj" / "Wyślij" are pinned under the scrolling part, so they sit right above the keyboard.
    SettingsLayout(title = "Zgłoś błąd", onBack = onBack, help = Help.REPORT, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f)) { shots.forEach { it.delete() }; onBack() }
            Box(Modifier.weight(1f)) { GoldButton(if (sending) "Wysyłam…" else "Wyślij", enabled = text.isNotBlank() && !sending, onClick = ::send) }
        }
    }) {
        error?.let { MessageBar(MessageKind.ERROR, it, onClose = { error = null }) }
        LuxuryCard(spacing = 12.dp) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ReportRules.MAX_TEXT) },
                label = { Text("Co się stało?") },
                placeholder = { Text("Np. „Dodałem zdjęcie do pozycji, ale się nie pokazało”") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Luxury.Outline),
            )
            LuxurySectionTitle(if (screen != null) "Zrzut ekranu" else "Zdjęcia (nieobowiązkowe)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shots.forEach { file ->
                    Box {
                        LocalShot(file, Modifier.width(84.dp).height(150.dp).clickable(onClickLabel = "Powiększ") { zoom = file })
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(40.dp).clip(CircleShape)
                                .clickable(role = Role.Button, onClickLabel = "Usuń zrzut") { shots.remove(file); file.delete() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Close, contentDescription = "Usuń zrzut", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                if (shots.size < ReportRules.MAX_SHOTS) {
                    Column(
                        modifier = Modifier.width(84.dp).height(150.dp).clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, Luxury.GoldLink, RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(AppIcons.Gallery, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(22.dp))
                        Text("Z galerii", fontSize = 12.sp, color = Luxury.GoldText)
                    }
                }
            }
            // What goes with it (board 2: folded; nothing about the kids' variant).
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (details) "Zwiń" else "Rozwiń") { details = !details },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Co dołączymy (wersja, telefon, ekran)", fontSize = 14.sp, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
                Text(if (details) "⌃" else "›", color = Luxury.GoldText)
            }
            if (details) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val probe = remember { newReport(context, "", screen, 0) }
                Muted(listOfNotNull(screen, "AMBiFy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").joinToString(" · "))
                Muted("${probe.phone} · Android ${probe.android}")
                Muted(listOfNotNull(me?.name, me?.familyName).joinToString(" · "))
            }
        }
    }
    zoom?.let { file -> ZoomDialog(onClose = { zoom = null }) { LocalShot(file, Modifier.fillMaxSize(), ContentScale.Fit) } }
}

/** "Moje zgłoszenia" (board 3): newest first; opening it clears the gold dot. */
@Composable
fun MyReportsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val session = LocalAppSession.current
    val context = LocalContext.current
    val mine by ReportsStore.mine.collectAsState()
    val waiting by ReportsStore.waiting.collectAsState()
    val seenAt by session.device.reportsSeenAt.collectAsState()
    // The dots stay for this visit; the next visit starts clean.
    val shownSeenAt = remember { seenAt }
    LaunchedEffect(Unit) {
        ReportsStore.refresh(context, session.api)
        session.device.setReportsSeen()
    }
    SettingsLayout(title = "Moje zgłoszenia", onBack = onBack, help = Help.MY_REPORTS) {
        val list = mine
        if (list == null && waiting.isEmpty()) {
            Text("Wczytuję…", color = Luxury.TextMuted)
        } else if (list.orEmpty().isEmpty() && waiting.isEmpty()) {
            MessageBar(MessageKind.INFO, "Nie masz jeszcze zgłoszeń", "Coś nie działa? Stuknij „?” na tym ekranie, a potem „Zgłoś błąd”.")
        } else {
            LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0.dp) {
                waiting.forEachIndexed { i, report ->
                    ReportRow(report.text, day(report.createdAt), dot = false, onClick = null) {
                        MessageChip(MessageKind.STATUS, "Czeka na wysłanie")
                    }
                }
                list.orEmpty().forEachIndexed { i, report ->
                    val fresh = (report.status == ReportStatus.FIXED || report.status == ReportStatus.EXPLAINED) && report.statusAt > shownSeenAt
                    ReportRow(report.text, day(report.createdAt), dot = fresh, onClick = { onOpen(report.id) }) { StatusChip(report) }
                }
            }
        }
    }
}

/** One report (boards 3 and 7): the team's answer first, then my description, screenshots and the steps. */
@Composable
fun MyReportScreen(reportId: String, onBack: () -> Unit) {
    val mine by ReportsStore.mine.collectAsState()
    val report = mine?.firstOrNull { it.id == reportId }
    var zoom by remember { mutableStateOf<String?>(null) }
    SettingsLayout(title = "Zgłoszenie", onBack = onBack, help = Help.MY_REPORTS) {
        if (report == null) {
            MessageBar(MessageKind.INFO, "Nie ma tego zgłoszenia")
            return@SettingsLayout
        }
        StatusChip(report)
        report.explanation?.let { answer ->
            LuxuryCard(spacing = 8.dp) {
                LuxurySectionTitle("Odpowiedź zespołu AMBiFy · ${day(report.statusAt)}")
                Text(answer, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
        LuxuryCard(spacing = 10.dp) {
            LuxurySectionTitle("Twój opis")
            Text(report.text, fontSize = 15.sp, lineHeight = 21.sp)
            if (report.shots.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                report.shots.forEach { path -> ServerShot(path, Modifier.width(70.dp).height(124.dp).clickable(onClickLabel = "Powiększ") { zoom = path }) }
            }
        }
        // B-009 (board 8): what went with the report, folded like "Co dołączymy" in the report window.
        if (report.appVersion != null || report.phone != null) {
            var attached by remember { mutableStateOf(false) }
            LuxuryCard(spacing = 6.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (attached) "Zwiń" else "Rozwiń") { attached = !attached },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Dołączone dane (wersja, telefon, ekran)", fontSize = 14.sp, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
                    Text(if (attached) "⌃" else "›", color = Luxury.GoldText)
                }
                if (attached) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(
                        report.screen?.let { "Ekran" to it },
                        report.appVersion?.let { "AMBiFy" to (it + (report.appCode?.let { c -> " ($c)" } ?: "")) },
                        report.phone?.let { "Telefon" to it },
                        report.android?.let { "Android" to it },
                        report.network?.let { "Sieć" to it },
                    ).forEach { (label, value) ->
                        Row {
                            Text(label, fontSize = 14.sp, color = Luxury.TextMuted, modifier = Modifier.width(88.dp))
                            Text(value, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        LuxuryCard(spacing = 8.dp) {
            LuxurySectionTitle("Historia zgłoszenia")
            report.history.forEach { step ->
                Text(
                    "${day(step.at)} · ${step.status.label.lowercase()}" + (step.version?.takeIf { step.status == ReportStatus.FIXED }?.let { " w $it" } ?: ""),
                    fontSize = 14.sp,
                )
            }
        }
    }
    zoom?.let { path -> ZoomDialog(onClose = { zoom = null }) { ServerShot(path, Modifier.fillMaxSize(), ContentScale.Fit) } }
}

/** A report's state as the reporter sees it. */
@Composable
internal fun StatusChip(report: MyReport) {
    val (kind, label) = when (report.status) {
        ReportStatus.SENT -> MessageKind.INFO to "Wysłane"
        ReportStatus.ACCEPTED -> MessageKind.STATUS to "Przyjęte"
        ReportStatus.IN_FIX -> MessageKind.STATUS to "W naprawie"
        ReportStatus.FIXED -> MessageKind.SUCCESS to ("Naprawione" + (report.fixedIn?.let { " w $it" } ?: ""))
        ReportStatus.EXPLAINED -> MessageKind.INFO to "Wyjaśnione"
    }
    MessageChip(kind, label)
}

@Composable
internal fun ReportRow(title: String, meta: String, dot: Boolean, onClick: (() -> Unit)?, chip: @Composable () -> Unit) {
    // B-016 (Michał, 27.11): each report between two thin lines with no sides — the top one bends down at its
    // ends, the bottom one bends up — so between two reports there is a double line.
    // 0.7.97: the theme divider (14 %) was barely visible; gold at 45 % reads on both dark and light themes.
    val line = Luxury.GoldText.copy(alpha = 0.45f)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 3.dp)
            .drawBehind {
                val r = 7.dp.toPx()
                val w = size.width
                val h = size.height
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                val top = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, r); quadraticTo(0f, 0f, r, 0f); lineTo(w - r, 0f); quadraticTo(w, 0f, w, r)
                }
                val bottom = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, h - r); quadraticTo(0f, h, r, h); lineTo(w - r, h); quadraticTo(w, h, w, h - r)
                }
                drawPath(top, line, style = stroke)
                drawPath(bottom, line, style = stroke)
            }
            .clip(RoundedCornerShape(7.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 15.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(meta, fontSize = 12.sp, color = Luxury.TextMuted, modifier = Modifier.weight(1f))
            if (dot) NewsDot()
            chip()
        }
    }
}

/**
 * B-011 (board 9, variant A): the one sign for "there is something new here for you to read or do" — a small
 * filled circle with "!" in the information colour, so it stands out next to the gold text and arrows.
 */
@Composable
fun NewsDot(modifier: Modifier = Modifier) {
    Box(
        modifier.size(14.dp).clip(CircleShape).background(Luxury.Info)
            .semantics { contentDescription = "Nowa odpowiedź" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "!", fontSize = 10.sp, lineHeight = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            color = if (Luxury.palette.light) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF0B0B0D),
        )
    }
}

@Composable
private fun Muted(text: String) = Text(text, fontSize = 13.sp, color = Luxury.TextMuted, lineHeight = 18.sp)

internal fun day(at: Long): String {
    val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
    val that = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(at))
    return if (today == that) "dziś, " + SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(at))
    else SimpleDateFormat("d.MM", Locale.ROOT).format(Date(at))
}

@Composable
internal fun ZoomDialog(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable(onClickLabel = "Zamknij", onClick = onClose).padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun LocalShot(file: File, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() }
    }
    ShotFrame(image, modifier, scale)
}

/** A screenshot from the server (my report's, or any in the Platform Admin's panel). */
@Composable
internal fun ServerShot(path: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val session = LocalAppSession.current
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            val file = File(File(context.cacheDir, "report-shots").apply { mkdirs() }, path.replace(Regex("[^A-Za-z0-9-]"), "_") + ".jpg")
            if (!file.isFile) runCatching { session.api.downloadReportShot(path, file) }.onFailure { file.delete() }
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    ShotFrame(image, modifier, scale)
}

@Composable
private fun ShotFrame(image: ImageBitmap?, modifier: Modifier, scale: ContentScale) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Luxury.Background).border(1.dp, Luxury.Outline, RoundedCornerShape(12.dp))) {
        if (image != null) Image(image, contentDescription = "Zrzut ekranu", contentScale = scale, modifier = Modifier.fillMaxSize())
    }
}

private fun saveJpeg(context: android.content.Context, bitmap: Bitmap): File? = runCatching {
    val file = File(File(context.cacheDir, "report-new").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    file
}.getOrNull()

private suspend fun picture(context: android.content.Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = maxOf(info.size.width, info.size.height)
            if (side > 1600) {
                val scale = 1600f / side
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        saveJpeg(context, bitmap)
    }.getOrNull()
}
