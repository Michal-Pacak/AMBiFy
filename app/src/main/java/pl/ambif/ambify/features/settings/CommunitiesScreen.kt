package pl.ambif.ambify.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import pl.ambif.ambify.features.account.AccountDialog
import pl.ambif.ambify.features.account.ButtonsRow
import pl.ambif.ambify.features.account.ConfirmDialog
import pl.ambif.ambify.features.account.HandOverDialog
import pl.ambif.ambify.features.account.InputDialog
import pl.ambif.ambify.features.account.PendingTransfer
import pl.ambif.ambify.features.account.label
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.ui.components.AvatarZoom
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryCardShape
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** What a popup action opens for one community. */
enum class CommunitySection { MEMBERS, INVITATIONS, MODULES, PERMISSIONS, REQUESTS, PHOTOS, LOOK }

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
    // Saved with the screen: back from a section (Role i uprawnienia…) shows this community's popup again, one step back.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = me.communities.firstOrNull { it.id == selectedId }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Board 16: the Owner hands the community over (true: and leaves it).
    var handOver by remember { mutableStateOf<Pair<CommunitySummary, Boolean>?>(null) }
    var avatarFor by remember { mutableStateOf<String?>(null) }

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

    SettingsLayout(title = "Kręgi", onBack = onBack, notice = notice, help = pl.ambif.ambify.ui.help.Help.CIRCLES) {
        // Each community is its own card: it only floats, the gold button opens its actions.
        me.communities.forEach { community ->
            LuxuryCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PersonAvatar(me.name, community.avatar, size = 44.dp, zoom = AvatarZoom.HOLD)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(community.name, style = MaterialTheme.typography.titleMedium)
                        // The badge sits next to the role: next to a long name it had no room and broke into letters.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(community.role.label(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (community.id == me.communityId) CurrentBadge()
                        }
                    }
                    LuxuryOpenButton("Pokaż akcje: ${community.name}", onClick = { selectedId = community.id })
                }
            }
            me.transfers.firstOrNull { it.communityId == community.id && !it.forMe }?.let { transfer ->
                PendingTransfer(transfer) { _, title, text -> notice = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ") }
            }
        }
        if (me.communities.isEmpty()) {
            Text("Nie należysz do żadnego kręgu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            ButtonsRow {
                FilledTonalButton(onClick = {
                    dialog = AccountDialog.Input(
                        title = "Nowy krąg",
                        label = "Nazwa kręgu",
                        info = "Zostaniesz jego Ownerem. Pierwszy własny krąg jest za darmo, każdy kolejny wymaga jednorazowej opłaty.",
                        action = "Załóż",
                        code = false,
                    ) { name ->
                        session.createCommunity(name)
                        notice = "Założono krąg $name."
                        dialog = null
                    }
                }) { Text("+ Załóż nową") }
                FilledTonalButton(onClick = {
                    dialog = AccountDialog.Input(
                        title = "Dołącz do kręgu",
                        label = "Kod zaproszenia",
                        info = "Kod dostajesz od Ownera lub Admina kręgu.",
                        action = "Dołącz",
                        code = true,
                    ) { code ->
                        session.joinCommunity(code)
                        notice = "Dołączono do kręgu ${(session.state.value as? SessionState.SignedIn)?.me?.familyName ?: ""}."
                        dialog = null
                    }
                }) { Text("+ Dołącz z kodem") }
            }
        }
    }

    // What my role may do in the community whose popup is open, and how many requests wait for me there.
    var popupMe by remember { mutableStateOf<Me?>(null) }
    var popupRequests by remember { mutableStateOf(0) }
    LaunchedEffect(selected?.id) {
        popupMe = null
        popupRequests = 0
        val id = selected?.id ?: return@LaunchedEffect
        val api = session.api.forCommunity(id)
        runCatching { popupMe = api.me() }
        runCatching { popupRequests = api.requests().size }
    }

    selected?.let { community ->
        CommunityPopup(
            me = me,
            community = community,
            permissions = popupMe?.permissions.orEmpty(),
            requests = popupRequests,
            onDismiss = { selectedId = null },
            onOpen = { section -> onOpen(community.id, section)
            },
            onGoogle = {
                selectedId = null
                notice = "Łączenie kalendarza Google z kręgiem będzie dostępne wkrótce."
            },
            onMakeCurrent = {
                selectedId = null
                act("Bieżący krąg: ${community.name}.") { session.switchCommunity(community.id) }
            },
            onHandOver = {
                selectedId = null
                handOver = community to false
            },
            onAvatar = {
                selectedId = null
                avatarFor = community.id
            },
            onRename = {
                selectedId = null
                dialog = AccountDialog.Input(
                    title = "Zmień nazwę kręgu",
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
                selectedId = null
                if (community.role.owner) {
                    handOver = community to true
                    return@CommunityPopup
                }
                dialog = AccountDialog.Confirm("Opuścić krąg ${community.name}?", "Opuść") {
                    act("Nie należysz już do kręgu ${community.name}.") { session.leaveCommunity(community.id) }
                }
            },
        )
    }

    avatarFor?.let { id ->
        val community = me.communities.firstOrNull { it.id == id }
        if (community != null) CircleAvatarDialog(community, session, onNotice = { notice = it }) { avatarFor = null }
    }
    handOver?.let { (community, leave) ->
        HandOverDialog(community, leave, onDismiss = { handOver = null }, outcome = { _, title, text -> notice = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ") })
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
    permissions: Map<Action, Mode>,
    requests: Int,
    onDismiss: () -> Unit,
    onOpen: (CommunitySection) -> Unit,
    onGoogle: () -> Unit,
    onMakeCurrent: () -> Unit,
    onRename: () -> Unit,
    onAvatar: () -> Unit,
    onHandOver: () -> Unit,
    onLeave: () -> Unit,
) {
    val owner = community.role.owner
    val can = { action: Action -> permissions[action] == Mode.ALLOWED }
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
                PersonAvatar(me.name, community.avatar, size = 46.dp, zoom = AvatarZoom.HOLD)
                Column {
                    Text(community.name, style = MaterialTheme.typography.titleLarge)
                    Text("Twoja rola: ${community.role.label()}", style = MaterialTheme.typography.bodyMedium, color = Luxury.GoldText)
                }
            }
            LuxurySectionTitle("Co możesz zrobić", modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            val manages = can(Action.ASSIGN_ROLES) || can(Action.REMOVE_MEMBERS)
            PopupAction(if (manages) "Członkowie" else "Członkowie (podgląd)") { onOpen(CommunitySection.MEMBERS) }
            if (can(Action.INVITE)) {
                LuxuryDivider()
                PopupAction("Zaproszenia") { onOpen(CommunitySection.INVITATIONS) }
            }
            if (requests > 0) {
                LuxuryDivider()
                PopupAction("Prośby do zatwierdzenia", trailing = requests.toString(), color = Luxury.GoldText) { onOpen(CommunitySection.REQUESTS) }
            }
            LuxuryDivider()
            PopupAction(if (can(Action.SWITCH_MODULES)) "Moduły" else "Moduły (podgląd)") { onOpen(CommunitySection.MODULES) }
            LuxuryDivider()
            PopupAction(if (owner) "Role i uprawnienia" else "Uprawnienia ról (podgląd)") { onOpen(CommunitySection.PERMISSIONS) }
            LuxuryDivider()
            PopupAction("Wygląd", detail = "motyw tego kręgu i Twój wybór") { onOpen(CommunitySection.LOOK) }
            if (can(Action.PHOTOS_MODERATE)) {
                LuxuryDivider()
                PopupAction("Zdjęcia profilowe", detail = if (community.photosBlocked) "wyłączone — wszyscy na inicjałach" else "włączone") { onOpen(CommunitySection.PHOTOS) }
            }
            if (can(Action.RENAME_COMMUNITY)) {
                LuxuryDivider()
                PopupAction("Zmień nazwę kręgu", onClick = onRename)
                LuxuryDivider()
                PopupAction("Awatar kręgu", detail = "zdjęcie albo litery na kolorze", onClick = onAvatar)
            }
            LuxuryDivider()
            PopupAction("Kalendarz Google", detail = "który Twój kalendarz łączyć z tym kręgiem", trailing = "wkrótce", onClick = onGoogle)
            if (community.id != me.communityId) {
                LuxuryDivider()
                PopupAction("Ustaw jako bieżącą", color = Luxury.GoldText, arrow = false, onClick = onMakeCurrent)
            }
            if (owner) {
                LuxuryDivider()
                PopupAction("Przekaż rolę Ownera", detail = "ktoś inny przejmie krąg, Ty zostaniesz", onClick = onHandOver)
            }
            // The Owner first picks who takes the community over (board 16).
            LuxuryDivider()
            PopupAction("Opuść krąg", color = Luxury.Error, arrow = false, onClick = onLeave)
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
        if (arrow && trailing == null) LuxuryOpenMark(size = 36.dp)
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
