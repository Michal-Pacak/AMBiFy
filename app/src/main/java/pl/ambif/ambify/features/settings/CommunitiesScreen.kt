package pl.ambif.ambify.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.account.AccountCard
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ButtonsRow
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.InputDialog
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCardShape
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** What a popup action opens for one community. */
enum class CommunitySection { MEMBERS, INVITATIONS, MINORS, MODULES }

/**
 * My communities. A tap opens a popup with my role there and the actions that role allows; they act on that
 * community even when it is not the current one.
 */
@Composable
fun CommunitiesScreen(onBack: () -> Unit, onOpen: (communityId: String, CommunitySection) -> Unit) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val me = (state as? SessionState.SignedIn)?.me ?: return
    var selected by remember { mutableStateOf<CommunitySummary?>(null) }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun act(done: String?, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                notice = done
            } catch (e: ServerError) {
                notice = e.message
            }
        }
    }

    SettingsLayout(title = "Społeczności", onBack = onBack, notice = notice) {
        AccountCard("Moje społeczności") {
            me.communities.forEachIndexed { index, community ->
                if (index > 0) LuxuryDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        // No grey highlight inside a Luxury card: the card itself answers the touch.
                        .clickable(interactionSource = null, indication = null, onClickLabel = "Pokaż akcje: ${community.name}") { selected = community },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PersonAvatar(me.name, community.avatar, size = 44.dp)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(community.name, style = MaterialTheme.typography.titleMedium)
                        // The badge sits next to the role: next to a long name it had no room and broke into letters.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(community.role.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (community.id == me.communityId) CurrentBadge()
                        }
                    }
                    Text("›", color = Luxury.GoldLink, fontSize = 24.sp)
                }
            }
            if (me.communities.isEmpty()) {
                Text("Nie należysz do żadnej społeczności.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Przy każdej społeczności jest Twoja ikona w niej. Gdy ktoś ma te same inicjały, ikony różnią się kształtem.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            ButtonsRow {
                FilledTonalButton(onClick = {
                    dialog = AccountDialog.Input(
                        title = "Nowa społeczność",
                        label = "Nazwa społeczności",
                        info = "Zostaniesz jej Ownerem. Pierwsza własna społeczność jest za darmo, każda kolejna wymaga jednorazowej opłaty.",
                        action = "Załóż",
                        code = false,
                    ) { name ->
                        session.createCommunity(name)
                        notice = "Założono społeczność $name."
                        dialog = null
                    }
                }) { Text("+ Załóż nową") }
                FilledTonalButton(onClick = {
                    dialog = AccountDialog.Input(
                        title = "Dołącz do społeczności",
                        label = "Kod zaproszenia",
                        info = "Kod dostajesz od Ownera lub Admina społeczności.",
                        action = "Dołącz",
                        code = true,
                    ) { code ->
                        session.joinCommunity(code)
                        notice = "Dołączono do społeczności ${(session.state.value as? SessionState.SignedIn)?.me?.familyName ?: ""}."
                        dialog = null
                    }
                }) { Text("+ Dołącz z kodem") }
            }
        }
    }

    selected?.let { community ->
        CommunityPopup(
            me = me,
            community = community,
            onDismiss = { selected = null },
            onOpen = { section ->
                selected = null
                onOpen(community.id, section)
            },
            onGoogle = {
                selected = null
                notice = "Łączenie kalendarza Google ze społecznością będzie dostępne wkrótce."
            },
            onMakeCurrent = {
                selected = null
                act("Bieżąca społeczność: ${community.name}.") { session.switchCommunity(community.id) }
            },
            onRename = {
                selected = null
                dialog = AccountDialog.Input(
                    title = "Zmień nazwę społeczności",
                    label = "Nowa nazwa",
                    info = null,
                    action = "Zapisz",
                    code = false,
                ) { name ->
                    session.api.forCommunity(community.id).renameCommunity(name)
                    session.refreshProfile()
                    notice = "Nowa nazwa: $name."
                    dialog = null
                }
            },
            onLeave = {
                selected = null
                dialog = AccountDialog.Confirm("Opuścić społeczność ${community.name}?", "Opuść") {
                    act("Nie należysz już do społeczności ${community.name}.") { session.leaveCommunity(community.id) }
                }
            },
        )
    }

    when (val current = dialog) {
        is AccountDialog.Input -> InputDialog(current, onDismiss = { dialog = null })
        is AccountDialog.Confirm -> ConfirmDialog(current, onDismiss = { dialog = null })
        else -> Unit
    }
}

/** Role-dependent actions for one community, in a gold popup; a tap outside closes it. */
@Composable
private fun CommunityPopup(
    me: Me,
    community: CommunitySummary,
    onDismiss: () -> Unit,
    onOpen: (CommunitySection) -> Unit,
    onGoogle: () -> Unit,
    onMakeCurrent: () -> Unit,
    onRename: () -> Unit,
    onLeave: () -> Unit,
) {
    val owner = community.role == Role.OWNER
    val manager = community.role.canManage
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, LuxuryCardShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(LuxuryCardShape)
                .background(Luxury.PopupBrush)
                .border(1.dp, Luxury.PopupBorderBrush, LuxuryCardShape)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(me.name, community.avatar, size = 46.dp)
                Column {
                    Text(community.name, style = MaterialTheme.typography.titleLarge)
                    Text("Twoja rola: ${community.role.label()}", style = MaterialTheme.typography.bodyMedium, color = Luxury.GoldText)
                }
            }
            LuxurySectionTitle("Co możesz zrobić", modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            PopupAction(if (owner) "Członkowie i role" else if (manager) "Członkowie" else "Członkowie (podgląd)") { onOpen(CommunitySection.MEMBERS) }
            if (manager) {
                LuxuryDivider()
                PopupAction("Zaproszenia") { onOpen(CommunitySection.INVITATIONS) }
                LuxuryDivider()
                PopupAction("Nieletni") { onOpen(CommunitySection.MINORS) }
            }
            LuxuryDivider()
            PopupAction(if (manager) "Moduły" else "Moduły (podgląd)") { onOpen(CommunitySection.MODULES) }
            if (owner) {
                LuxuryDivider()
                PopupAction("Zmień nazwę społeczności", onClick = onRename)
            }
            LuxuryDivider()
            PopupAction("Kalendarz Google", detail = "który Twój kalendarz łączyć z tą społecznością", trailing = "wkrótce", onClick = onGoogle)
            if (community.id != me.communityId) {
                LuxuryDivider()
                PopupAction("Ustaw jako bieżącą", color = Luxury.GoldText, arrow = false, onClick = onMakeCurrent)
            }
            if (owner) {
                LuxuryDivider()
                Text(
                    "Owner nie może opuścić społeczności. Przekazanie roli Ownera — w planach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Luxury.Disabled,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                LuxuryDivider()
                PopupAction("Opuść społeczność", color = Color(0xFFFF8A80), arrow = false, onClick = onLeave)
            }
        }
    }
}

@Composable
private fun PopupAction(
    label: String,
    detail: String? = null,
    trailing: String? = null,
    color: Color = Luxury.Text,
    arrow: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Luxury.Disabled) }
        if (arrow && trailing == null) Text("›", color = Luxury.GoldLink, fontSize = 22.sp)
    }
}

@Composable
private fun CurrentBadge() {
    Text(
        "bieżąca",
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Luxury.GoldBase.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = Luxury.GoldLight,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
    )
}
