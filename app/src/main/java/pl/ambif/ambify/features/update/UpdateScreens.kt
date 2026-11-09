package pl.ambif.ambify.features.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.alpha
import pl.ambif.ambify.ui.theme.LuxuryPopup
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.modules.update.UpdateState
import pl.ambif.ambify.shared.AppRelease
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val Outline = Color(0xFF5A5A60)
private val Green = Color(0xFF9AD7A4)
private val Red = Color(0xFFFF8A80)

/** What the update popup does; [later] closes it (the pulsing "Aktualizacja" button stays). */
class UpdateActions(
    val installNow: (AppRelease) -> Unit,
    val installAtNight: (AppRelease) -> Unit,
    val later: () -> Unit,
    val cancelNight: () -> Unit,
)

/** The gold "dalej" action, as wide as its text (the wide one is [pl.ambif.ambify.features.timetable.GoldButton]). */
@Composable
private fun GoldPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Luxury.GoldFill)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Luxury.Surface) }
}

@Composable
private fun Framed(label: String, gold: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (gold) Luxury.GoldLink else Outline),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(label, color = if (gold) Luxury.GoldText else Luxury.TextMuted, fontSize = 14.sp) }
}

@Composable
private fun Muted(text: String) = Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = Luxury.TextMuted)

/** "0.6.1 → 0.7.0", "11 z 18 MB". */
private fun mb(bytes: Long) = "%.0f".format(bytes / 1_048_576.0)

/** The popup over any screen: a newer version, its download, the night plan or what went wrong. */
@Composable
fun UpdatePopup(state: UpdateState, actions: UpdateActions) {
    LuxuryPopup(onDismiss = actions.later) { UpdateContent(state, actions, inPopup = true) }
}

/** The same as a card (the forced screen shows the download and the install this way). */
@Composable
fun UpdateCard(state: UpdateState, actions: UpdateActions) {
    LuxuryCard(contentPadding = PaddingValues(18.dp)) { UpdateContent(state, actions, inPopup = false) }
}

/** Something to do with an update: the popup has more to say than [UpToDate][UpdateState.UpToDate]. */
fun UpdateState.hasNews() = this is UpdateState.Available || this is UpdateState.Downloading ||
    this is UpdateState.Installing || this is UpdateState.Scheduled || this is UpdateState.Failed

/**
 * Always at the top of the screen while there is a newer version: the gold "Aktualizacja" button, pulsing until
 * the update is planned for the night. Opens the popup again.
 */
@Composable
fun UpdateBadge(state: UpdateState, onClick: () -> Unit) {
    if (!state.hasNews()) return
    val night = state is UpdateState.Scheduled
    val pulse by rememberInfiniteTransition(label = "update").animateFloat(
        initialValue = 1f,
        targetValue = if (night) 1f else 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 6.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                // The frame stays put; only the gold inside (and the label) pulses.
                .then(if (night) Modifier else Modifier.background(Luxury.GoldFill, RoundedCornerShape(17.dp), alpha = pulse))
                .border(1.dp, if (night) Luxury.GoldLink else Luxury.GoldBase, RoundedCornerShape(17.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (night) "Aktualizacja o 3:00" else "⟳  Aktualizacja",
                modifier = Modifier.alpha(pulse),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (night) Luxury.GoldText else Luxury.Surface,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.UpdateContent(state: UpdateState, actions: UpdateActions, inPopup: Boolean) {
    when (state) {
        is UpdateState.Available -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LuxurySectionTitle("Nowa wersja", modifier = Modifier.weight(1f))
                Muted("${BuildConfig.VERSION_NAME} → ${state.release.versionName}")
            }
            Text("AMBiFy ${state.release.versionName} jest gotowa", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            state.release.notes.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 20.sp, color = Luxury.TextMuted) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldPill("Zaktualizuj") { actions.installNow(state.release) }
                Framed("W nocy", gold = true) { actions.installAtNight(state.release) }
                if (inPopup) Framed("Później", gold = false, onClick = actions.later)
            }
            Text("Twoje dane i logowanie zostają.", fontSize = 12.sp, color = Luxury.Disabled)
        }
        is UpdateState.Downloading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LuxurySectionTitle("Pobieranie", modifier = Modifier.weight(1f))
                Text("${(state.progress * 100).toInt()}%", fontSize = 13.sp, color = Luxury.GoldText)
            }
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Luxury.GoldBase,
                trackColor = Color(0xFF2A2A30),
            )
            Muted(
                "Pobieram AMBiFy ${state.release.versionName} z serwera… " +
                    "${mb((state.release.sizeBytes * state.progress).toLong())} z ${mb(state.release.sizeBytes)} MB",
            )
            if (inPopup) Framed("Ukryj", gold = false, onClick = actions.later)
        }
        is UpdateState.Installing -> {
            LuxurySectionTitle("Instalacja")
            Muted("Instaluję AMBiFy ${state.release.versionName}. Jeśli Android zapyta, wybierz „Aktualizuj”.")
        }
        is UpdateState.Scheduled -> {
            LuxurySectionTitle("Aktualizacja zaplanowana")
            Text("${state.release.versionName} zainstaluje się w nocy, gdy nie używasz telefonu", fontSize = 14.sp, lineHeight = 20.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldPill("Zaktualizuj teraz") { actions.installNow(state.release) }
                Framed("Anuluj noc", gold = true, onClick = actions.cancelNight)
                if (inPopup) Framed("Zamknij", gold = false, onClick = actions.later)
            }
        }
        is UpdateState.Failed -> {
            LuxurySectionTitle("Aktualizacja się nie udała")
            Text(state.message, fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFFFFC9C3))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldPill("Spróbuj ponownie") { actions.installNow(state.release) }
                if (inPopup) Framed("Później", gold = false, onClick = actions.later)
            }
        }
        else -> Unit
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(28.dp).border(1.dp, Luxury.GoldLink, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldText) }
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The one-time Android permission, explained (mock-up 15). Comes back by itself once allowed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallPermissionScreen(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    FullScreen {
        ScreenTitle("Aktualizacja", "", onCancel)
        LuxuryCard(contentPadding = PaddingValues(18.dp), spacing = 14.dp) {
            LuxurySectionTitle("Jednorazowa zgoda")
            Text("Pozwól AMBiFy instalować swoje aktualizacje", fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 23.sp)
            Text(
                "AMBiFy nie pochodzi ze Sklepu Play, tylko z naszego serwera domowego. " +
                    "Android pyta więc raz, czy ta aplikacja może się sama aktualizować.",
                fontSize = 14.sp, lineHeight = 21.sp, color = Luxury.TextMuted,
            )
            Step(1, "Stuknij „Otwórz ustawienia”.")
            Step(2, "Włącz „Zezwalaj z tego źródła”.")
            Step(3, "Wróć strzałką. Aktualizacja ruszy sama.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldPill("Otwórz ustawienia", onOpenSettings)
                Framed("Anuluj", gold = false, onClick = onCancel)
            }
        }
        Text(
            "Ekran pojawia się tylko przy pierwszej aktualizacji. Nazwa przełącznika zależy od producenta telefonu.",
            fontSize = 12.sp, lineHeight = 17.sp, color = Luxury.Disabled, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
}

/** Instead of the whole app when it no longer works with the server (mock-up 16): only updating is left. */
@Composable
fun ForcedUpdateScreen(state: UpdateState, release: AppRelease, onUpdate: () -> Unit) {
    FullScreen(center = true) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AMBiFy", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = Luxury.GoldText)
            Text("wersja ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = Luxury.Disabled)
        }
        when (state) {
            is UpdateState.Downloading, is UpdateState.Installing -> UpdateCard(state, actions = NoActions)
            else -> LuxuryCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp), spacing = 14.dp) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LuxurySectionTitle("Nowa wersja")
                    Text("AMBiFy ${release.versionName} już czeka", fontSize = 19.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(
                        "Wprowadziliśmy zmiany, które działają tylko w nowej wersji. Jedno stuknięcie i wszystko będzie gotowe. " +
                            "Twoje dane i logowanie zostają.",
                        fontSize = 14.sp, lineHeight = 21.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center,
                    )
                    (state as? UpdateState.Failed)?.let { Text(it.message, fontSize = 13.sp, color = Red, textAlign = TextAlign.Center) }
                    GoldPill("Zaktualizuj teraz", onUpdate)
                }
            }
        }
    }
}

private val NoActions = UpdateActions({}, {}, {}, {})

@Composable
private fun FullScreen(center: Boolean = false, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Luxury.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = if (center) Arrangement.spacedBy(20.dp, Alignment.CenterVertically) else Arrangement.spacedBy(16.dp),
    ) { content() }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pl"))
private val dayFormat = SimpleDateFormat("d.MM", Locale.forLanguageTag("pl"))

/** "dziś 09:14", "wczoraj 21:03", "31.10 08:00". */
internal fun checkedText(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return "jeszcze nie sprawdzono"
    fun day(at: Long) = Calendar.getInstance().apply { timeInMillis = at }.let { it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR) }
    val time = timeFormat.format(Date(millis))
    return "Sprawdzono " + when (day(now) - day(millis)) {
        0 -> "dziś $time"
        1 -> "wczoraj $time"
        else -> "${dayFormat.format(Date(millis))} $time"
    }
}

/** Ustawienia → Aplikacja (mock-up 17): this version, checking by hand, and the link for a new person. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppVersionCard(state: UpdateState, lastChecked: Long, checking: Boolean, onCheck: () -> Unit, onShareLink: () -> Unit, onUpdate: (AppRelease) -> Unit) {
    val newer = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Failed -> state.release
        else -> null
    }
    LuxuryCard(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp)) {
        LuxurySectionTitle("Aplikacja")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wersja ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val (label, color) = when (state) {
                UpdateState.UpToDate -> "AKTUALNA" to Green
                is UpdateState.Scheduled -> "W NOCY ${state.release.versionName}" to Luxury.GoldText
                is UpdateState.Downloading, is UpdateState.Installing -> "AKTUALIZACJA…" to Luxury.GoldText
                UpdateState.Unknown -> null to Luxury.TextMuted
                else -> newer?.let { "JEST ${it.versionName}" } to Luxury.GoldText
            }
            label?.let {
                Text(
                    it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
                    modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            if (checking) "Sprawdzam…" else "${checkedText(lastChecked)} · aktualizacje z serwera domowego",
            fontSize = 12.sp, color = Luxury.TextMuted,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (newer != null) GoldPill("Zaktualizuj do ${newer.versionName}") { onUpdate(newer) }
            Framed("Sprawdź aktualizacje", gold = true, onClick = onCheck)
            Framed("Wyślij link do pobrania", gold = true, onClick = onShareLink)
        }
    }
}
