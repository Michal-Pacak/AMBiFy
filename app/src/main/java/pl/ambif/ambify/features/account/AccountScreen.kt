package pl.ambif.ambify.features.account

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.Device
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Role

@Composable
fun AccountScreen() {
    val session = LocalAppSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val manager = me.role.canManage

    var family by remember { mutableStateOf<Family?>(null) }
    var devices by remember { mutableStateOf<List<Device>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    var serverStatus by remember { mutableStateOf<ServerStatus>(ServerStatus.Checking) }
    var diagnostics by remember { mutableStateOf<DiagnosticsState?>(null) }

    suspend fun reload() {
        family = api.family()
        devices = api.devices()
    }

    /** Runs a change on the server, then refreshes the cards; a refusal becomes a notice. */
    fun act(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                notice = done
                reload()
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }

    LaunchedEffect(Unit) {
        serverStatus = api.status()
        try {
            reload()
        } catch (e: ServerError) {
            notice = e.message
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Konto", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }

            ProfileCard(
                me = me,
                onChangePassword = { dialog = AccountDialog.ChangePassword },
                onLogout = { scope.launch { session.logout() } },
            )

            family?.let { current ->
                FamilyCard(
                    family = current,
                    me = me,
                    onInvite = {
                        act {
                            val invitation = api.createInvitation()
                            dialog = AccountDialog.Code(
                                title = "Kod zaproszenia",
                                text = "Przekaż kod osobie, która ma dołączyć. Wpisze go po wybraniu „Mam kod zaproszenia”. Kod jest jednorazowy, ważny do ${formatDay(invitation.expiresAt)}.",
                                code = invitation.code,
                                share = invitationText(current.name, invitation.code, invitation.expiresAt),
                            )
                        }
                    },
                    onAddChild = { dialog = AccountDialog.Child(null) },
                    onEditChild = { dialog = AccountDialog.Child(it) },
                    onDeleteChild = { dialog = AccountDialog.Confirm("Usunąć profil ${it.name}?", "Usuń") { act { api.deleteChild(it.id) } } },
                    onSetRole = { member, role ->
                        act("${member.name}: rola ${role.label()}.") { api.setRole(member.id, role) }
                    },
                    onSignOutMember = { member ->
                        act("Wylogowano urządzenia: ${member.name}.") { api.signOutMember(member.id) }
                    },
                    onResetPassword = { member ->
                        act {
                            val reset = api.createResetCode(member.id)
                            dialog = AccountDialog.Code(
                                title = "Kod resetu hasła: ${member.name}",
                                text = "${member.name} wybiera na ekranie logowania „Nie pamiętam hasła” i wpisuje ten kod. Ważny 24 godziny.",
                                code = reset.code,
                                share = null,
                            )
                        }
                    },
                    onRemoveMember = { member ->
                        dialog = AccountDialog.Confirm("Usunąć ${member.name} ze społeczności?", "Usuń") {
                            act("${member.name} nie należy już do społeczności.") { api.removeMember(member.id) }
                        }
                    },
                )
                if (manager && current.invitations.isNotEmpty()) {
                    InvitationsCard(
                        invitations = current.invitations,
                        onShare = { share(invitationText(current.name, it.code, it.expiresAt)) },
                        onCancel = { act { api.cancelInvitation(it.code) } },
                    )
                }
            }

            DevicesCard(devices = devices, onSignOut = { device -> act("Wylogowano: ${device.name}.") { api.signOutDevice(device.id) } })

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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kalendarz Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nie połączono konta. Po połączeniu będzie można wybierać synchronizację osobno dla każdego wydarzenia.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Owner lub Admin będzie mógł połączyć także prywatny kalendarz osoby nieletniej.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    diagnostics?.let { DiagnosticsDialog(state = it, onDismiss = { diagnostics = null }) }

    when (val current = dialog) {
        null -> Unit
        AccountDialog.ChangePassword -> ChangePasswordDialog(
            onDismiss = { dialog = null },
            onSave = { request ->
                // Reload before closing: closing the dialog cancels the work it started.
                api.changePassword(request)
                notice = "Hasło zmienione. Inne urządzenia zostały wylogowane."
                reload()
                dialog = null
            },
        )
        is AccountDialog.Code -> CodeDialog(current, onShare = current.share?.let { text -> { share(text) } }, onDismiss = { dialog = null })
        is AccountDialog.Child -> ChildDialog(
            child = current.child,
            onDismiss = { dialog = null },
            onSave = { name, color ->
                val request = ChildRequest(name, color)
                if (current.child == null) api.addChild(request) else api.updateChild(current.child.id, request)
                reload()
                dialog = null
            },
        )
        is AccountDialog.Confirm -> ConfirmDialog(current, onDismiss = { dialog = null })
    }
}

private val dayFormat = SimpleDateFormat("dd.MM", Locale("pl", "PL"))

internal fun formatDay(millis: Long): String = dayFormat.format(Date(millis))

private fun invitationText(familyName: String, code: String, expiresAt: Long) =
    "Dołącz do społeczności $familyName w AMBiFy. Kod zaproszenia: $code (ważny do ${formatDay(expiresAt)})."

internal val Gold = Color(0xFFD4A93C)
