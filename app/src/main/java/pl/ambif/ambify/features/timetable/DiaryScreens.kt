package pl.ambif.ambify.features.timetable

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.shared.DiaryConnection
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.TimetableWeek
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val Ok = Color(0xFF9AD7A4)
private val Failed = Color(0xFFFF8A80)
private val Info = Color(0xFF8EC3F0)
private val Outline = Color(0xFF5A5A60)

/** The gold main action of a screen. */
@Composable
internal fun GoldButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Luxury.GoldFill else androidx.compose.ui.graphics.SolidColor(Outline))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Luxury.Surface) }
}

/** A bordered action: gold for the usual ones, grey for "Odłącz", "Anuluj". */
@Composable
internal fun FramedButton(label: String, gold: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, if (gold) Luxury.GoldLink else Outline),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, color = if (gold) Luxury.GoldText else Color(0xFFC9C9CF), fontSize = 14.sp) }
}

/**
 * No timetable yet (the community has no school diary connected): who may connect it gets the way to do it,
 * the others learn who can.
 */
@Composable
internal fun NoTimetable(week: TimetableWeek, canLink: Boolean, communityName: String, onConnect: () -> Unit, onExtras: (() -> Unit)?) {
    if (canLink) {
        LuxuryCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Połącz z dziennikiem szkolnym", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    "AMBiFy pobierze plan lekcji Twoich dzieci z eduVulcan, razem z zastępstwami i odwołanymi lekcjami, i będzie go sama odświeżać.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9C9CF), textAlign = TextAlign.Center,
                )
            }
            LuxuryDivider()
            Step(1, "Logujesz się na stronie eduVulcan danymi rodzica — tymi samymi co w przeglądarce.")
            Step(2, "AMBiFy znajduje Twoje dzieci i pokazuje je do sprawdzenia.")
            Step(3, "Plan pojawia się od razu u wszystkich w społeczności.")
            GoldButton("Połącz z eduVulcan", onClick = onConnect)
            Text(
                "Inne dzienniki (Librus, Mobidziennik) — w przygotowaniu.",
                fontSize = 12.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        LuxuryCard {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Planu jeszcze nie ma", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Plan pojawi się tutaj, gdy Owner lub Admin połączy społeczność „$communityName” z dziennikiem eduVulcan.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9C9CF), textAlign = TextAlign.Center,
                )
                if (week.diaryLinkers.isNotEmpty()) {
                    Text("Kto może to zrobić: ${week.diaryLinkers.joinToString()}.", fontSize = 13.sp, color = Luxury.TextMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (onExtras != null) {
        LuxuryCard {
            LuxurySectionTitle("W MIĘDZYCZASIE")
            Text("Zajęcia dodatkowe możesz dodawać już teraz — pojawią się w planie obok lekcji.", style = MaterialTheme.typography.bodyMedium)
            FramedButton("+  Zajęcia dodatkowe", onClick = onExtras)
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(26.dp).border(1.dp, Luxury.GoldLink, CircleShape), contentAlignment = Alignment.Center) {
            Text("$number", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldText)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private enum class ConnectStep { INTRO, LOGIN, LINKING, PUPILS }

/**
 * „Połącz z eduVulcan”: what will happen, the eduvulcan.pl sign-in inside the app (the password stays on Vulcan's
 * page; the app only reads the one-time tokens of https://eduvulcan.pl/api/ap and hands them to the server), then
 * the pupils found and which member's account each of them is.
 */
@Composable
internal fun DiaryConnectScreen(members: List<Member>, onBack: () -> Unit, onDone: () -> Unit) {
    val api = LocalAppSession.current.api
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(ConnectStep.INTRO) }
    var error by remember { mutableStateOf<String?>(null) }
    var connection by remember { mutableStateOf<DiaryConnection?>(null) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    BackHandler { if (step == ConnectStep.LOGIN) step = ConnectStep.INTRO else if (step != ConnectStep.LINKING) onBack() }

    fun connect(tokens: List<String>) {
        step = ConnectStep.LINKING
        scope.launch {
            try {
                connection = api.connectVulcan(tokens)
                students = api.timetable().students
                step = ConnectStep.PUPILS
            } catch (e: ServerError) {
                error = e.message
                step = ConnectStep.INTRO
            }
        }
    }

    when (step) {
        ConnectStep.LOGIN -> Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().background(Luxury.Surface).padding(horizontal = 8.dp, vertical = 8.dp)) {
                ScreenTitle("Logowanie do eduVulcan", "krok 2 z 3 · eduvulcan.pl", onBack = { step = ConnectStep.INTRO })
                Text(
                    "Hasło wpisujesz na stronie Vulcana. AMBiFy go nie widzi. Po zalogowaniu przejdziemy dalej sami.",
                    fontSize = 12.sp, color = Color(0xFFC9C9CF), modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            EduVulcanLogin(modifier = Modifier.weight(1f).fillMaxWidth(), onTokens = ::connect)
        }
        ConnectStep.LINKING -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Luxury.GoldBase)
            Text("Szukam Twoich dzieci w dzienniku i pobieram plan…", textAlign = TextAlign.Center)
        }
        ConnectStep.INTRO -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTitle("Połącz z eduVulcan", "krok 1 z 3", onBack = onBack)
            error?.let { Notice(it, Failed) }
            LuxuryCard {
                LuxurySectionTitle("CO SIĘ STANIE")
                Text("Otworzy się strona logowania eduvulcan.pl. Zaloguj się jako rodzic — tym samym e-mailem i hasłem co zwykle.", style = MaterialTheme.typography.bodyMedium)
                Text("AMBiFy nie poznaje Twojego hasła. Wpisujesz je na stronie Vulcana, a AMBiFy dostaje od niego tylko dostęp do odczytu dziennika (tak jak aplikacja mobilna) i pobiera z niego plan.", style = MaterialTheme.typography.bodyMedium)
                Text("Potem plan odświeża się sam, codziennie o 18:00 (i od razu, gdy stukniesz „Odśwież teraz”).", style = MaterialTheme.typography.bodyMedium)
                Text("Połączenie odłączysz w każdej chwili w Ustawieniach planu.", style = MaterialTheme.typography.bodyMedium)
            }
            Notice("Nie pamiętasz hasła? Na stronie logowania jest „Nie pamiętam hasła” — szkoła nie musi nic robić.", Info)
            GoldButton(if (error == null) "Zaloguj się do eduVulcan" else "Spróbuj jeszcze raz") {
                error = null
                step = ConnectStep.LOGIN
            }
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onBack)
        }
        ConnectStep.PUPILS -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val found = connection?.pupils.orEmpty()
            ScreenTitle("Połącz z eduVulcan", "krok 3 z 3", onBack = null)
            Notice("Połączono. W dzienniku ${pupilsWord(found.size)}.", Ok)
            Text(
                "Czy któreś dziecko ma własne konto w AMBiFy? Wtedy zobaczy swój plan jako „mój” i dostanie swoje przypomnienia.",
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC9C9CF),
            )
            found.forEach { pupil ->
                val student = students.firstOrNull { it.id == pupil.studentId }
                LuxuryCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(pupil.name, student?.avatar, size = 40.dp)
                        Column {
                            Text(pupil.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(listOf("klasa ${pupil.clazz}", pupil.school).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Luxury.TextMuted)
                        }
                    }
                    Text("Konto w AMBiFy", fontSize = 12.sp, color = Luxury.TextMuted)
                    AccountChips(members, student?.memberId) { memberId ->
                        scope.launch {
                            try {
                                students = api.linkStudent(pupil.studentId, memberId)
                            } catch (e: ServerError) {
                                error = e.message
                            }
                        }
                    }
                }
            }
            error?.let { Notice(it, Failed) }
            Text("Możesz to zmienić później w Ustawieniach planu → Uczniowie i konta.", fontSize = 12.sp, color = Luxury.TextMuted)
            GoldButton("Gotowe — pokaż plan", onClick = onDone)
        }
    }
}

private fun pupilsWord(count: Int) = when {
    count == 1 -> "jest 1 uczeń"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "są $count uczniowie"
    else -> "jest $count uczniów"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(members: List<Member>, selected: String?, onPick: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<Pair<String?, String>>(null to "Nie ma konta") + members.map { it.id to it.name }).forEach { (id, label) ->
            val on = id == selected
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) Luxury.GoldBase.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (on) Luxury.GoldLink else Color(0xFF4A4A50), RoundedCornerShape(20.dp))
                    .clickable { if (!on) onPick(id) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, color = if (on) Luxury.GoldLight else Color(0xFFC9C9CF)) }
        }
    }
}

@Composable
private fun Notice(text: String, color: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 14.sp, color = color) }
}

/**
 * eduvulcan.pl in a WebView. After each page of eduvulcan.pl the app asks /api/ap (same site, the page's own
 * sign-in) in the background: before signing in it is the sign-in page, after it the tokens. The cookies are cleared
 * before (another parent may sign in) and after (the eduVulcan session does not stay on the phone).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EduVulcanLogin(modifier: Modifier, onTokens: (List<String>) -> Unit) {
    var done by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        onDispose { CookieManager.getInstance().removeAllCookies(null) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            CookieManager.getInstance().removeAllCookies(null)
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun ap(text: String) {
                        val tokens = apTokens(text) ?: return
                        main.post {
                            if (!done) {
                                done = true
                                onTokens(tokens)
                            }
                        }
                    }
                }, "AMBiFy")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (!done && Uri.parse(url).host?.endsWith("eduvulcan.pl") == true) view.evaluateJavascript(FETCH_AP, null)
                    }
                }
                loadUrl("https://eduvulcan.pl/logowanie")
            }
        },
    )
}

/** Reads /api/ap and hands its JSON (the value of the hidden field holding the tokens) to the app. */
private const val FETCH_AP = """
(function () {
  fetch('/api/ap', { credentials: 'include' }).then(function (r) { return r.text(); }).then(function (t) {
    var found = '';
    try {
      new DOMParser().parseFromString(t, 'text/html').querySelectorAll('input').forEach(function (i) {
        if (!found && (i.value || '').indexOf('Tokens') >= 0) found = i.value;
      });
    } catch (e) {}
    if (!found && t.indexOf('"Tokens"') >= 0) found = t.substring(t.indexOf('{'), t.lastIndexOf('}') + 1);
    AMBiFy.ap(found);
  }).catch(function () { AMBiFy.ap(''); });
})();
"""

/** The tokens from /api/ap ({"Success":true,"Tokens":[…]}), or null while not signed in. */
internal fun apTokens(text: String): List<String>? {
    if (text.isBlank()) return null
    val answer = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
    if (answer["Success"]?.jsonPrimitive?.booleanOrNull != true) return null
    return runCatching { answer["Tokens"]!!.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Ustawienia planu: the connected parent accounts of the diary and which student is which member. */
@Composable
internal fun TimetableSettingsScreen(
    students: List<Student>,
    members: List<Member>,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onChanged: () -> Unit,
) {
    val api = LocalAppSession.current.api
    val scope = rememberCoroutineScope()
    var connections by remember { mutableStateOf<List<DiaryConnection>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<DiaryConnection?>(null) }
    var linked by remember(students) { mutableStateOf(students) }
    BackHandler(onBack = onBack)

    fun act(id: String?, block: suspend () -> Unit) {
        busy = id
        scope.launch {
            try {
                block()
            } catch (e: ServerError) {
                notice = e.message
            } finally {
                busy = null
            }
        }
    }
    LaunchedEffect(Unit) { act(null) { connections = api.diaryConnections() } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Ustawienia planu", "", onBack = onBack)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
        LuxuryCard {
            LuxurySectionTitle("DZIENNIK SZKOLNY")
            val list = connections
            if (list == null) CircularProgressIndicator(color = Luxury.GoldBase, modifier = Modifier.size(24.dp))
            else if (list.isEmpty()) Text("Nie połączono jeszcze żadnego konta rodzica.", style = MaterialTheme.typography.bodyMedium, color = Luxury.TextMuted)
            list.orEmpty().forEach { connection ->
                ConnectionBox(
                    connection,
                    busy = busy == connection.id,
                    onSync = {
                        act(connection.id) {
                            val fresh = api.syncDiary(connection.id)
                            connections = connections.orEmpty().map { if (it.id == fresh.id) fresh else it }
                            notice = if (fresh.lastError == null) "Plan pobrany z eduVulcan." else null
                            onChanged()
                        }
                    },
                    onReconnect = onConnect,
                    onDisconnect = { confirm = connection },
                )
            }
            FramedButton("+  Dodaj konto rodzica", onClick = onConnect)
            Text(
                "Drugie konto przydaje się, gdy dzieci są w różnych szkołach. Ten sam uczeń z dwóch kont pojawia się raz.",
                fontSize = 12.sp, color = Luxury.TextMuted,
            )
        }
        if (linked.isNotEmpty()) {
            LuxuryCard {
                LuxurySectionTitle("UCZNIOWIE I KONTA")
                linked.forEachIndexed { index, student ->
                    if (index > 0) LuxuryDivider()
                    var menu by remember { mutableStateOf(false) }
                    val member = members.firstOrNull { it.id == student.memberId }
                    Box {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { menu = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PersonAvatar(student.name, student.avatar, size = 30.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${student.name} · ${student.clazz}", fontSize = 15.sp)
                                Text(member?.let { "konto AMBiFy: ${it.name}" } ?: "bez konta AMBiFy", fontSize = 12.sp, color = Luxury.TextMuted)
                            }
                            LuxuryOpenMark(size = 36.dp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            (listOf<Pair<String?, String>>(null to "Bez konta") + members.map { it.id to it.name }).forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    menu = false
                                    act(null) {
                                        linked = api.linkStudent(student.id, id)
                                        onChanged()
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    confirm?.let { connection ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Odłączyć eduVulcan?") },
            text = { Text("Plan przestanie się aktualizować z tego konta. Uczniowie, ich dotychczasowe lekcje i zajęcia dodatkowe zostają.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = null
                    act(connection.id) {
                        api.disconnectDiary(connection.id)
                        connections = connections.orEmpty().filter { it.id != connection.id }
                        onChanged()
                    }
                }) { Text("Odłącz") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Anuluj") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionBox(connection: DiaryConnection, busy: Boolean, onSync: () -> Unit, onReconnect: () -> Unit, onDisconnect: () -> Unit) {
    val failed = connection.lastError != null
    val color = if (failed) Failed else Ok
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (failed) Failed.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (failed) Failed.copy(alpha = 0.35f) else Color(0xFF34343A), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("eduVulcan", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                if (failed) "BŁĄD" else "DZIAŁA",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (connection.addedByName.isNotBlank()) Text("Połączył(a): ${connection.addedByName}", fontSize = 13.sp, color = Color(0xFFC9C9CF))
        Text(
            "Uczniowie: " + connection.pupils.joinToString { if (it.clazz.isBlank()) it.name else "${it.name} (${it.clazz})" }.ifBlank { "—" },
            fontSize = 13.sp, color = Color(0xFFC9C9CF),
        )
        connection.lastError?.let { Text(it, fontSize = 13.sp, color = Color(0xFFFFC9C3)) }
        Text("Ostatnia aktualizacja: ${syncTime(connection.lastSyncAt)} · następna ${nextSync()}", fontSize = 12.sp, color = Luxury.TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) CircularProgressIndicator(color = Luxury.GoldBase, modifier = Modifier.size(24.dp).padding(2.dp))
            else if (failed) FramedButton("Połącz ponownie", onClick = onReconnect)
            else FramedButton("Odśwież teraz", onClick = onSync)
            if (failed && !busy) FramedButton("Spróbuj teraz", onClick = onSync)
            FramedButton("Odłącz", gold = false, onClick = onDisconnect)
        }
    }
}

private fun syncTime(at: Long?): String {
    if (at == null) return "jeszcze nie było"
    val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val clock = "${time.hour}:%02d".format(time.minute)
    return if (time.toLocalDate() == LocalDate.now()) "dziś $clock" else "${time.toLocalDate().dayMonth()} $clock"
}

/** The server updates every connection at 18:00. */
private fun nextSync(): String = if (LocalTime.now().isBefore(LocalTime.of(18, 0))) "dziś o 18:00" else "jutro o 18:00"
