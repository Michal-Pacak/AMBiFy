package pl.ambif.ambify.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.account.AccountCard
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ButtonsRow
import pl.ambif.ambify.features.account.ChangePasswordDialog
import pl.ambif.ambify.features.account.DevicesCard
import pl.ambif.ambify.features.account.DiagnosticsDialog
import pl.ambif.ambify.features.account.DiagnosticsState
import pl.ambif.ambify.features.account.EditProfileDialog
import pl.ambif.ambify.features.account.ScreenTitle
import pl.ambif.ambify.features.account.ServerCard
import pl.ambif.ambify.features.account.ServerStatus
import pl.ambif.ambify.features.account.SwitchRow
import pl.ambif.ambify.features.account.diagnosticsState
import pl.ambif.ambify.features.account.downloadLink
import pl.ambif.ambify.features.update.AppVersionCard
import pl.ambif.ambify.modules.update.AppUpdater
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.features.account.status
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.ui.components.AvatarZoom
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** Ustawienia from the module bar: Aplikacja (with Wygląd), Konto and Kręgi — A to Z. */
@Composable
fun SettingsScreen(onOpenAccount: () -> Unit, onOpenCommunities: () -> Unit, onOpenApp: () -> Unit) {
    val session = LocalAppSession.current
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val reports by pl.ambif.ambify.features.reports.ReportsStore.mine.collectAsState()
    val seenAt by session.device.reportsSeenAt.collectAsState()
    val reportNews = pl.ambif.ambify.features.reports.ReportsStore.news(reports, seenAt)
    // B-011: fetch my reports each time Ustawienia opens, so a new answer shows its "!" without signing in again.
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { pl.ambif.ambify.features.reports.ReportsStore.refresh(context, session.api) }

    SettingsLayout(title = "Ustawienia", onBack = null, help = pl.ambif.ambify.ui.help.Help.SETTINGS) {
        // A–Z (Michał, 26.11): Aplikacja (with Wygląd inside), Konto, Kręgi.
        val themes by session.themes.collectAsState()
        HubCard(onClick = onOpenApp, title = "Aplikacja") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AMBiFy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                // 0.7.9: an answer to my report is waiting (board 3: the dot climbs up to here, not to the bar).
                if (reportNews) pl.ambif.ambify.features.reports.NewsDot()
            }
            val look = themes?.accountTheme?.let { key -> themeName(themes!!, key) } ?: "motyw kręgu"
            Text(
                "Wygląd: $look · animacje, aktualizacje",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HubCard(onClick = onOpenAccount, title = "Konto") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(me.name, me.myAvatar(), size = 52.dp, zoom = AvatarZoom.HOLD)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(me.name, style = MaterialTheme.typography.titleMedium)
                    Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HubCard(onClick = onOpenCommunities, title = "Kręgi") {
            Text(me.familyName ?: "Brak kręgu", style = MaterialTheme.typography.titleMedium)
            if (me.communities.size > 1) Text(
                "i ${me.communities.size - 1} ${if (me.communities.size - 1 == 1) "inny" else "inne"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (BuildConfig.TEST_LOGIN) ScrollTestCards()
    }
}

/** Konto: own data, password, connected accounts (later), signed-in devices and signing out. */
@Composable
fun AccountSettingsScreen(onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = session.api
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                devices = api.devices()
                notice = done
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    LaunchedEffect(Unit) { act {} }

    SettingsLayout(title = "Konto", onBack = onBack, notice = notice, help = pl.ambif.ambify.ui.help.Help.ACCOUNT) {
        LuxuryCard(contentPadding = PaddingValues(20.dp), spacing = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // My photo here even where a community switched photos off.
                PersonAvatar(me.name, me.myAvatar()?.copy(photo = me.photo), size = 96.dp)
                Text(me.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ButtonsRow {
                FilledTonalButton(onClick = { dialog = AccountDialog.EditProfile }) { Text("Zmień dane") }
                FilledTonalButton(onClick = { dialog = AccountDialog.ChangePassword }) { Text("Zmień hasło") }
            }
        }
        AccountCard("Zdjęcie profilowe") { ProfilePhotoPart(me, session) { notice = it } }
        AccountCard("Połączone konta") {
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "kalendarze do synchronizacji z kręgami",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("wkrótce", style = MaterialTheme.typography.bodyMedium, color = Luxury.Disabled)
            }
        }
        DevicesCard(devices = devices, onSignOut = { device -> act("Wylogowano: ${device.name}.") { api.signOutDevice(device.id) } })
        OutlinedButton(onClick = { scope.launch { session.logout() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Text("Wyloguj z tego telefonu", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    when (dialog) {
        AccountDialog.EditProfile -> EditProfileDialog(
            me = me,
            onDismiss = { dialog = null },
            onSave = { update ->
                session.updateProfile(update)
                notice = "Dane zapisane."
                dialog = null
            },
        )
        AccountDialog.ChangePassword -> ChangePasswordDialog(
            onDismiss = { dialog = null },
            onSave = { request ->
                // Reload before closing: closing the dialog cancels the work it started.
                api.changePassword(request)
                notice = "Hasło zmienione. Inne urządzenia zostały wylogowane."
                devices = api.devices()
                dialog = null
            },
        )
        else -> Unit
    }
}

/** Aplikacja: settings of this phone — the server connection and the card animations. */
@Composable
fun AppSettingsScreen(onBack: () -> Unit, onOpenLook: () -> Unit, onReportBug: () -> Unit = {}, onMyReports: () -> Unit = {}) {
    val session = LocalAppSession.current
    val api = session.api
    val scope = rememberCoroutineScope()
    val cardAnimations by session.device.cardAnimations.collectAsState()
    var serverStatus by remember { mutableStateOf<ServerStatus>(ServerStatus.Checking) }
    var diagnostics by remember { mutableStateOf<DiagnosticsState?>(null) }

    val context = LocalContext.current
    val updater = remember { AppUpdater.of(context) }
    val update by updater.state.collectAsState()
    var checking by remember { mutableStateOf(false) }
    var lastChecked by remember { mutableStateOf(updater.lastChecked) }

    LaunchedEffect(Unit) { serverStatus = api.status() }

    SettingsLayout(title = "Aplikacja", onBack = onBack, help = pl.ambif.ambify.ui.help.Help.APP) {
        AccountCard("Wygląd") {
            // Wygląd lives here now (Michał, 26.11): my theme first, then the cards' animations.
            val themes by session.themes.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Otwórz Wygląd", onClick = onOpenLook).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mój motyw", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text((themes?.accountTheme?.let { key -> themeName(themes!!, key) } ?: "motyw kręgu") + "  ›", color = Luxury.GoldText)
            }
            SwitchRow("Animacje kart", cardAnimations, enabled = true) { session.device.setCardAnimations(it) }
            if (session.device.systemAnimationsOff()) Text(
                "Animacje są wyłączone w ustawieniach Androida, więc karty stoją nieruchomo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 0.7.9 (board 1): reporting a bug without "?" and my reports with their answers.
        AccountCard("Błędy") {
            val reports by pl.ambif.ambify.features.reports.ReportsStore.mine.collectAsState()
            val waiting by pl.ambif.ambify.features.reports.ReportsStore.waiting.collectAsState()
            val seenAt by session.device.reportsSeenAt.collectAsState()
            SettingsRow("Zgłoś błąd", "›", onReportBug)
            SettingsRow(
                "Moje zgłoszenia",
                "${reports.orEmpty().size + waiting.size}  ›",
                onMyReports,
                dot = pl.ambif.ambify.features.reports.ReportsStore.news(reports, seenAt),
            )
        }
        ServerCard(
            status = serverStatus,
            onCheckConnection = {
                diagnostics = DiagnosticsState.Loading
                serverStatus = ServerStatus.Checking
                scope.launch {
                    diagnostics = api.diagnosticsState()
                    serverStatus = api.status()
                }
            },
        )
        AppVersionCard(
            state = update,
            lastChecked = lastChecked,
            checking = checking,
            onCheck = {
                checking = true
                updater.check {
                    checking = false
                    lastChecked = updater.lastChecked
                }
            },
            onShareLink = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Pobierz aplikację AMBiFy (działa w domowym Wi‑Fi): $downloadLink")
                context.startActivity(Intent.createChooser(send, "Wyślij link do pobrania"))
            },
            onUpdate = updater::installNow,
        )
    }

    diagnostics?.let { DiagnosticsDialog(state = it, onDismiss = { diagnostics = null }) }
}

@Composable
private fun SettingsRow(label: String, value: String, onClick: () -> Unit, dot: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = label, onClick = onClick).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (dot) pl.ambif.ambify.features.reports.NewsDot()
        Text(value, color = Luxury.GoldText)
    }
}

/** The person's own icon in the current community. */
internal fun Me.myAvatar() = communities.firstOrNull { it.id == communityId }?.avatar


@Composable
internal fun SettingsLayout(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String = "",
    notice: String? = null,
    help: pl.ambif.ambify.ui.help.HelpScreen? = null,
    /** Buttons pinned under the scrolling part (forms): always on screen, right above the keyboard when it is up. */
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ScreenTitle(title, subtitle, onBack, help)
                notice?.let { pl.ambif.ambify.ui.components.MessageBar(pl.ambif.ambify.ui.components.MessageKind.INFO, it) }
                content()
            }
            if (footer != null) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) { footer() }
        }
    }
}

/** AMBiFy TEST only: sample cards so the screen scrolls and the floating cards can be tried while scrolling. */
@Composable
private fun ScrollTestCards() {
    repeat(5) { index ->
        LuxuryCard(contentPadding = PaddingValues(18.dp), spacing = 6.dp) {
            LuxurySectionTitle("Karta przykładowa ${index + 1}")
            Text(
                "Tylko w AMBiFy TEST — do sprawdzenia przewijania i kołysania kart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A card of the Ustawienia screen that opens a section. */
@Composable
private fun HubCard(onClick: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    // The card only floats; the gold button opens the section.
    LuxuryCard(contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LuxurySectionTitle(title)
                content()
            }
            LuxuryOpenButton("Otwórz: $title", onClick = onClick, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
