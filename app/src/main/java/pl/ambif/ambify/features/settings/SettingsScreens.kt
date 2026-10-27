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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.features.account.status
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** Ustawienia from the module bar: Konto, Społeczności and Aplikacja (settings of this phone). */
@Composable
fun SettingsScreen(onOpenAccount: () -> Unit, onOpenCommunities: () -> Unit, onOpenApp: () -> Unit) {
    val session = LocalAppSession.current
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val cardAnimations by session.device.cardAnimations.collectAsState()

    SettingsLayout(title = "Ustawienia", onBack = null) {
        HubCard(onClick = onOpenAccount, title = "Konto") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(me.name, me.myAvatar(), size = 52.dp)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(me.name, style = MaterialTheme.typography.titleMedium)
                    Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HubCard(onClick = onOpenCommunities, title = "Społeczności") {
            Text(me.familyName ?: "Brak społeczności", style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    me.role?.let { "bieżąca · ${it.label()}" },
                    "razem ${me.communities.size} ${communitiesWord(me.communities.size)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HubCard(onClick = onOpenApp, title = "Aplikacja") {
            Text("AMBiFy ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Animacje kart: ${if (cardAnimations) "włączone" else "wyłączone"} · ten telefon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (me.platformAdmin) {
            Text(
                "Panel Platform Admina jest osobną pozycją na pasku.",
                style = MaterialTheme.typography.bodySmall,
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

    SettingsLayout(title = "Konto", onBack = onBack, notice = notice) {
        LuxuryCard(contentPadding = PaddingValues(20.dp), spacing = 8.dp) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PersonAvatar(me.name, me.myAvatar(), size = 84.dp)
                Text(me.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                Text(me.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("zdjęcie profilowe — wkrótce", style = MaterialTheme.typography.bodySmall, color = Luxury.Disabled)
            }
            ButtonsRow {
                FilledTonalButton(onClick = { dialog = AccountDialog.EditProfile }) { Text("Zmień dane") }
                FilledTonalButton(onClick = { dialog = AccountDialog.ChangePassword }) { Text("Zmień hasło") }
            }
        }
        AccountCard("Połączone konta") {
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "kalendarze do synchronizacji ze społecznościami",
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
fun AppSettingsScreen(onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = session.api
    val scope = rememberCoroutineScope()
    val cardAnimations by session.device.cardAnimations.collectAsState()
    var serverStatus by remember { mutableStateOf<ServerStatus>(ServerStatus.Checking) }
    var diagnostics by remember { mutableStateOf<DiagnosticsState?>(null) }

    LaunchedEffect(Unit) { serverStatus = api.status() }

    SettingsLayout(title = "Aplikacja", onBack = onBack) {
        AccountCard("Wygląd") {
            SwitchRow("Animacje kart", cardAnimations, enabled = true) { session.device.setCardAnimations(it) }
            Text(
                if (session.device.systemAnimationsOff()) "Animacje są wyłączone w ustawieniach Androida, więc karty stoją nieruchomo."
                else "Karty lekko uginają się pod palcem i puszczają złote kręgi. Ustawienie dotyczy tylko tego telefonu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }

    diagnostics?.let { DiagnosticsDialog(state = it, onDismiss = { diagnostics = null }) }
}

/** The person's own icon in the current community. */
internal fun Me.myAvatar() = communities.firstOrNull { it.id == communityId }?.avatar

private fun communitiesWord(count: Int) = if (count == 1) "społeczność" else "społeczności"

@Composable
internal fun SettingsLayout(
    title: String,
    onBack: (() -> Unit)?,
    subtitle: String = "",
    notice: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenTitle(title, subtitle, onBack)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
            content()
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
