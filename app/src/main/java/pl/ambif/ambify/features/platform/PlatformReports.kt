package pl.ambif.ambify.features.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.reports.ReportRow
import pl.ambif.ambify.features.reports.ServerShot
import pl.ambif.ambify.features.reports.StatusChip
import pl.ambif.ambify.features.reports.ZoomDialog
import pl.ambif.ambify.features.reports.day
import pl.ambif.ambify.features.settings.SettingsLayout
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.PlatformReport
import pl.ambif.ambify.shared.ReportRules
import pl.ambif.ambify.shared.ReportStatus
import pl.ambif.ambify.shared.ReportUpdate
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageChip
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.help.Help
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle
import pl.ambif.ambify.ui.theme.PopupButtons

/** All reports as last fetched, shared by the card, the list and one report. */
private object PlatformReportsStore {
    val all = MutableStateFlow<List<PlatformReport>?>(null)
    suspend fun refresh(api: ServerApi) { runCatching { api.platformReports() }.onSuccess { all.value = it } }
}

/** The card at the top of the Platform screen (board 4): counts and the way in. */
@Composable
internal fun PlatformReportsCard(onOpen: () -> Unit) {
    val api = LocalAppSession.current.api
    val all by PlatformReportsStore.all.collectAsState()
    LaunchedEffect(Unit) { PlatformReportsStore.refresh(api) }
    val fresh = all.orEmpty().count { it.report.status == ReportStatus.SENT }
    val fixing = all.orEmpty().count { it.report.status == ReportStatus.IN_FIX }
    LuxuryCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // B-008: the chip sits under the title (beside it, it got squeezed and broke into letters on the S24).
                LuxurySectionTitle("Zgłoszenia błędów")
                if (fresh > 0) MessageChip(MessageKind.STATUS, "$fresh ${pl.ambif.ambify.features.lists.plural(fresh, "nowe", "nowe", "nowych")}")
                Text(
                    if (all == null) "Wczytuję…" else "$fixing w naprawie · ${all.orEmpty().size} razem",
                    fontSize = 14.sp, color = Luxury.TextMuted,
                )
            }
            LuxuryOpenButton("Otwórz: Zgłoszenia", onClick = onOpen, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

private enum class Filter(val label: String, val statuses: Set<ReportStatus>) {
    // B-017: "Wszystkie" first and chosen by default.
    ALL("Wszystkie", ReportStatus.entries.toSet()),
    NEW("Nowe", setOf(ReportStatus.SENT)),
    ACCEPTED("Przyjęte", setOf(ReportStatus.ACCEPTED)),
    FIXING("W naprawie", setOf(ReportStatus.IN_FIX)),
    DONE("Naprawione", setOf(ReportStatus.FIXED)),
    EXPLAINED("Wyjaśnione", setOf(ReportStatus.EXPLAINED)),
}

/** The list (board 4): filters by state; who, circle, screen, when; "Dla dzieci" only here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformReportsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val api = LocalAppSession.current.api
    val all by PlatformReportsStore.all.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    LaunchedEffect(Unit) { PlatformReportsStore.refresh(api) }
    SettingsLayout(title = "Zgłoszenia", onBack = onBack, help = Help.PLATFORM_REPORTS) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Filter.entries.forEach { f ->
                val count = all.orEmpty().count { it.report.status in f.statuses }
                Chip("${f.label} $count", on = f == filter) { filter = f }
            }
        }
        val shown = all.orEmpty().filter { it.report.status in filter.statuses }
        if (all != null && shown.isEmpty()) {
            MessageBar(MessageKind.INFO, "Nic tu nie ma")
        } else if (shown.isNotEmpty()) {
            LuxuryCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), spacing = 0.dp) {
                shown.forEachIndexed { i, item ->
                    val meta = listOfNotNull(item.reporterName, item.circle, item.report.screen, day(item.report.createdAt)).joinToString(" · ")
                    ReportRow(item.report.text, meta, dot = false, onClick = { onOpen(item.report.id) }) {
                        if (item.kids) MessageChip(MessageKind.INFO, "Dla dzieci")
                        item.number?.let { Text(it, fontSize = 12.sp, color = Luxury.GoldText) }
                    }
                }
            }
        }
    }
}

private val Reasons = listOf(
    "Tak ma działać" to "Tak ma działać: ",
    "Nie da się powtórzyć" to "Nie udało nam się tego powtórzyć. Jeśli zdarzy się znowu, zgłoś to od razu — zrzut bardzo pomoże.",
    "Już naprawione" to "To już działa w nowszej wersji. Zaktualizuj aplikację: Ustawienia → Aplikacja.",
    "Pomysł, nie błąd" to "Dzięki za pomysł! Zapisaliśmy go na listę rzeczy do rozważenia.",
)

private sealed interface Sheet {
    data object Accept : Sheet
    data object Fix : Sheet
    data object Explain : Sheet
}

/** One report (boards 4 and 7): text, screenshots, the phone's data, and the state change. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformReportScreen(reportId: String, onBack: () -> Unit) {
    val api = LocalAppSession.current.api
    val scope = rememberCoroutineScope()
    val all by PlatformReportsStore.all.collectAsState()
    val item = all?.firstOrNull { it.report.id == reportId }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var notice by remember { mutableStateOf<Pair<MessageKind, String>?>(null) }
    var zoom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (all == null) PlatformReportsStore.refresh(api) }

    fun change(update: ReportUpdate, done: String) {
        scope.launch {
            try {
                api.updateReport(reportId, update)
                PlatformReportsStore.refresh(api)
                sheet = null
                notice = MessageKind.SUCCESS to done
            } catch (e: ServerError) {
                notice = MessageKind.ERROR to e.message
            }
        }
    }

    SettingsLayout(title = "Zgłoszenie", onBack = onBack, help = Help.PLATFORM_REPORTS) {
        if (item == null) {
            if (all != null) MessageBar(MessageKind.INFO, "Nie ma tego zgłoszenia")
            return@SettingsLayout
        }
        val report = item.report
        notice?.let { (kind, text) -> MessageBar(kind, text, onClose = { notice = null }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(report)
            item.number?.let { Text(it, color = Luxury.GoldText, fontWeight = FontWeight.Medium) }
        }
        LuxuryCard(spacing = 10.dp) {
            Text(report.text, fontSize = 15.sp, lineHeight = 21.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                report.shots.forEach { path -> ServerShot(path, Modifier.width(96.dp).height(170.dp).clickable(onClickLabel = "Powiększ") { zoom = path }) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Data("Kto", item.reporterName)
                Data("Krąg", item.circle ?: "—")
                Data("Ekran", report.screen ?: "z Ustawień")
                Data("Wersja", "${item.appVersion} (${item.appCode})")
                Data("Telefon", "${item.phone} · Android ${item.android}")
                Data("Wariant", if (item.kids) "dla dzieci" else "zwykła")
                Data("Sieć", item.network ?: "—")
                Data("Kiedy", day(report.createdAt))
            }
            report.explanation?.let { Data("Wyjaśnienie", it) }
            report.fixedIn?.let { Data("Naprawa w", it) }
        }
        if (report.status != ReportStatus.FIXED) {
            LuxurySectionTitle("Zmień stan", modifier = Modifier.padding(start = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.status == ReportStatus.SENT) Box(Modifier.width(220.dp)) { GoldButton("Przyjmij do rejestru") { sheet = Sheet.Accept } }
                FramedButton("W naprawie") { sheet = Sheet.Fix }
                FramedButton("To nie błąd", gold = false) { sheet = Sheet.Explain }
            }
        }
    }

    val current = item ?: return
    when (sheet) {
        Sheet.Accept -> {
            val next = remember(all) {
                val top = all.orEmpty().mapNotNull { it.number?.removePrefix("B-")?.toIntOrNull() }.maxOrNull()
                top?.let { "B-%03d".format(it + 1) } ?: "B-"
            }
            OneField("Przyjmij do rejestru", "Numer w rejestrze błędów", next, "Zgłaszający zobaczy stan „Przyjęte”.", "Przyjmij",
                onDismiss = { sheet = null }) { change(ReportUpdate(ReportStatus.ACCEPTED, number = it), "Przyjęte jako ${it.uppercase()}.") }
        }
        Sheet.Fix -> OneField("W naprawie", "Wersja z naprawą", current.report.fixedIn ?: BuildConfig.VERSION_NAME.substringBefore("-"),
            "Gdy ta wersja trafi do wszystkich, stan zmieni się sam na „Naprawione”, a zgłaszający zobaczy to w „Co nowego”.", "Zapisz",
            onDismiss = { sheet = null }) { change(ReportUpdate(ReportStatus.IN_FIX, fixedIn = it), "W naprawie, wersja $it.") }
        Sheet.Explain -> ExplainSheet(onDismiss = { sheet = null }) { change(ReportUpdate(ReportStatus.EXPLAINED, explanation = it), "Wysłano wyjaśnienie.") }
        null -> Unit
    }
    zoom?.let { path -> ZoomDialog(onClose = { zoom = null }) { ServerShot(path, Modifier.fillMaxSize(), ContentScale.Fit) } }
}

/** "To nie błąd" (board 7): a reason gives the start of the answer; nothing goes without words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplainSheet(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var reason by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf(Reasons[0].second) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text("To nie błąd", fontSize = 20.sp, fontFamily = Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text("Zgłaszający zobaczy tę odpowiedź w „Moje zgłoszenia”. Nie może na nią odpisać.", fontSize = 14.sp, color = Luxury.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Reasons.forEachIndexed { i, (label, start) -> Chip(label, on = i == reason) { reason = i; text = start } }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(ReportRules.MAX_EXPLANATION) },
            label = { Text("Wyjaśnienie") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Luxury.Outline),
        )
        // "Tak ma działać: " alone says nothing: a sentence more is needed.
        val ready = text.trim().length >= ReportRules.MIN_EXPLANATION && text.trim() != Reasons[0].second.trim()
        PopupButtons {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                Box(Modifier.weight(1.4f)) { GoldButton("Wyślij wyjaśnienie", enabled = ready) { onSend(text.trim()) } }
            }
        }
    }
}

@Composable
private fun OneField(title: String, label: String, start: String, info: String, action: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(start) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text(title, fontSize = 20.sp, fontFamily = Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value, onValueChange = { value = it.take(20) }, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Luxury.Outline),
        )
        Text(info, fontSize = 13.sp, color = Luxury.TextMuted, lineHeight = 18.sp)
        PopupButtons {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                Box(Modifier.weight(1f)) { GoldButton(action, enabled = value.isNotBlank()) { onSave(value.trim()) } }
            }
        }
    }
}

@Composable
private fun Data(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, fontSize = 13.sp, color = Luxury.TextMuted, modifier = Modifier.width(86.dp))
        Text(value, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier.heightIn(min = 40.dp).clip(shape)
            .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.dp, Luxury.GoldLink, shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, color = if (on) Luxury.OnGold else Luxury.Text, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal) }
}
