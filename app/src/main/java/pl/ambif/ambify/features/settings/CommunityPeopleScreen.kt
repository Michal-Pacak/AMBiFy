package pl.ambif.ambify.features.settings

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ChildDialog
import pl.ambif.ambify.features.account.CodeDialog
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.InvitationsCard
import pl.ambif.ambify.features.account.MembersCard
import pl.ambif.ambify.features.account.MinorsCard
import pl.ambif.ambify.features.account.formatDay
import pl.ambif.ambify.features.account.invitationText
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.shared.ChildRequest
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Role

/** Members, invitations or minors of one community (not necessarily the current one), by my role there. */
@Composable
fun CommunityPeopleScreen(communityId: String, section: CommunitySection, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = remember(communityId) { session.api.forCommunity(communityId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    val myRole = me.communities.firstOrNull { it.id == communityId }?.role ?: Role.MEMBER
    var family by remember { mutableStateOf<Family?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    suspend fun reload() {
        family = api.family()
    }

    /** Runs a change on the server, then refreshes the list; a refusal becomes a notice. */
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

    LaunchedEffect(communityId) { act {} }

    val title = when (section) {
        CommunitySection.MEMBERS -> "Członkowie"
        CommunitySection.INVITATIONS -> "Zaproszenia"
        else -> "Nieletni"
    }
    SettingsLayout(title = title, onBack = onBack, subtitle = family?.name ?: "", notice = notice) {
        val current = family ?: return@SettingsLayout
        when (section) {
            CommunitySection.MEMBERS -> MembersCard(
                family = current,
                myRole = myRole,
                onSetRole = { member, role -> act("${member.name}: rola ${role.label()}.") { api.setRole(member.id, role) } },
                onSignOutMember = { member -> act("Wylogowano urządzenia: ${member.name}.") { api.signOutMember(member.id) } },
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
            CommunitySection.INVITATIONS -> InvitationsCard(
                invitations = current.invitations,
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
                onShare = { share(invitationText(current.name, it.code, it.expiresAt)) },
                onCancel = { act { api.cancelInvitation(it.code) } },
            )
            else -> MinorsCard(
                children = current.children,
                manager = myRole.canManage,
                onAdd = { dialog = AccountDialog.Child(null) },
                onEdit = { dialog = AccountDialog.Child(it) },
                onDelete = { child -> dialog = AccountDialog.Confirm("Usunąć profil ${child.name}?", "Usuń") { act { api.deleteChild(child.id) } } },
            )
        }
    }

    when (val current = dialog) {
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
        else -> Unit
    }
}
