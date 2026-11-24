package pl.ambif.ambify.features.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.ambif.ambify.features.account.AccountCard
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.CodeDialog
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.InvitationsCard
import pl.ambif.ambify.features.account.MembersCard
import pl.ambif.ambify.features.account.RolePickerDialog
import pl.ambif.ambify.features.account.formatDay
import pl.ambif.ambify.features.account.invitationText
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Mode

/** Members or invitations of one community (not necessarily the current one), as the caller's role there allows. */
@Composable
fun CommunityPeopleScreen(communityId: String, section: CommunitySection, onBack: () -> Unit) {
    val session = LocalAppSession.current
    val api = remember(communityId) { session.api.forCommunity(communityId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var family by remember { mutableStateOf<Family?>(null) }
    // Me in that community: my role there and what it may do.
    var me by remember { mutableStateOf<Me?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }

    suspend fun reload() {
        me = api.me()
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
        CommunitySection.INVITATIONS -> "Zaproszenia"
        CommunitySection.PHOTOS -> "Zdjęcia profilowe"
        else -> "Członkowie"
    }
    SettingsLayout(title = title, onBack = onBack, subtitle = family?.name ?: "", notice = notice) {
        val current = family ?: return@SettingsLayout
        val myRole = me?.role ?: return@SettingsLayout
        val can = { action: Action -> me?.permissions?.get(action) == Mode.ALLOWED }
        when (section) {
            CommunitySection.PHOTOS -> PhotosCard(
                family = current,
                onBlocked = { blocked ->
                    act(if (blocked) "Zdjęcia wyłączone — wszyscy tutaj na inicjałach." else "Zdjęcia włączone.") { api.setPhotosBlocked(blocked) }
                },
                onShow = { hidden -> act("Zdjęcie: ${hidden.name} — znów widoczne.") { api.setPhotoHidden(hidden.memberId, false) } },
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
            else -> MembersCard(
                family = current,
                myRole = myRole,
                can = can,
                onChangeRole = { member -> dialog = AccountDialog.PickRole(member) },
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
        }
    }

    when (val current = dialog) {
        is AccountDialog.Code -> CodeDialog(current, onShare = current.share?.let { text -> { share(text) } }, onDismiss = { dialog = null })
        is AccountDialog.Confirm -> ConfirmDialog(current, onDismiss = { dialog = null })
        is AccountDialog.PickRole -> {
            val roles = family?.roles.orEmpty()
            val myRole = me?.role
            if (myRole != null) {
                RolePickerDialog(current.member, roles, myRole, onDismiss = { dialog = null }) { role ->
                    api.setRole(current.member.id, role.id)
                    notice = "${current.member.name}: rola ${role.name}."
                    reload()
                    dialog = null
                }
            }
        }
        else -> Unit
    }
}

/** Canvas "Zdjęcia profilowe", board 2 A: photos on or off here, and the hidden ones with "Pokaż ponownie". */
@Composable
private fun PhotosCard(family: Family, onBlocked: (Boolean) -> Unit, onShow: (pl.ambif.ambify.shared.HiddenPhoto) -> Unit) {
    AccountCard("Zdjęcia profilowe") {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = androidx.compose.ui.semantics.Role.Switch) { onBlocked(!family.photosBlocked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text("Pokazuj zdjęcia osób", fontSize = 15.sp)
                Text("Wyłączone: wszyscy tutaj na inicjałach", fontSize = 12.sp, color = Luxury.TextMuted)
            }
            androidx.compose.material3.Switch(checked = !family.photosBlocked, onCheckedChange = { onBlocked(!it) })
        }
        Text("Ukryte zdjęcia · ${family.hiddenPhotos.size}", fontSize = 12.sp, color = Luxury.TextMuted, modifier = Modifier.padding(top = 6.dp))
        if (family.hiddenPhotos.isEmpty()) {
            Text("Nikt. Zdjęcie jednej osoby ukrywasz stuknięciem w jej awatar → „Ukryj zdjęcie w tej społeczności”.", fontSize = 12.sp, color = Luxury.TextMuted)
        }
        family.hiddenPhotos.forEach { hidden ->
            val member = family.members.firstOrNull { it.id == hidden.memberId }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                PersonAvatar(hidden.name, member?.avatar, size = 36.dp)
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(hidden.name, fontSize = 15.sp)
                    Text("ukrył(a): ${hidden.byName} · ${formatDay(hidden.at)}", fontSize = 12.sp, color = Luxury.TextMuted)
                }
                androidx.compose.material3.TextButton(onClick = { onShow(hidden) }) { Text("Pokaż ponownie", color = Luxury.GoldText) }
            }
        }
    }
}
