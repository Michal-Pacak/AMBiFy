package pl.ambif.ambify.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.AppSession
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.features.lists.DangerButton
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.CommunitySummary
import pl.ambif.ambify.shared.Family
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.OwnerTransfer
import pl.ambif.ambify.ui.components.FieldError
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.components.AvatarZoom
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.PopupButtons
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** What an action ended with, for the screen's bubble: kind, title, text. */
typealias Outcome = (MessageKind, String, String) -> Unit

private val Outline: androidx.compose.ui.graphics.Color get() = Luxury.Outline

/** "23.11" from a time in milliseconds. */
internal fun shortDate(at: Long): String {
    val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    return "%d.%02d".format(date.dayOfMonth, date.monthValue)
}

/** Runs [block]; a server or connection problem becomes an error bubble. */
private fun AppSession.attempt(scope: kotlinx.coroutines.CoroutineScope, outcome: Outcome, block: suspend AppSession.() -> Unit) {
    scope.launch {
        try {
            block()
        } catch (e: ServerError) {
            outcome(MessageKind.ERROR, "Nie udało się", e.message.orEmpty())
        } catch (e: IOException) {
            outcome(MessageKind.ERROR, "Brak połączenia z serwerem", "Spróbuj ponownie, gdy wróci internet.")
        }
    }
}

/**
 * Board 16 A: the Owner picks who takes the community over ([leave]: and leaves it; else stays with the highest
 * other role). People in the order of the hint: the highest role first, then who joined earlier. Alone in the
 * community with [leave]: removing it instead (board 16 D).
 */
@Composable
fun HandOverDialog(community: CommunitySummary, leave: Boolean, onDismiss: () -> Unit, outcome: Outcome) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    var family by remember { mutableStateOf<Family?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(community.id) {
        try {
            family = session.api.forCommunity(community.id).family()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
        }
    }
    val meId = (session.state.value as? pl.ambif.ambify.core.session.SessionState.SignedIn)?.me?.id
    val people = family?.members.orEmpty().filter { it.id != meId }
        .sortedWith(compareByDescending<Member> { it.role.rank }.thenBy { it.joinedAt ?: Long.MAX_VALUE })
    val loaded = family
    if (loaded != null && people.isEmpty() && leave) {
        DeleteCommunityDialog(community, onDismiss, outcome)
        return
    }
    var picked by remember(people.firstOrNull()?.id) { mutableStateOf(people.firstOrNull()?.id) }
    val stayRole = loaded?.roles?.firstOrNull { !it.owner }?.label() ?: "Admin"
    LuxuryPopup(onDismiss = onDismiss) {
        Text(if (leave) "Opuścić „${community.name}”?" else "Przekaż rolę Ownera", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        MessageBar(
            MessageKind.INFO,
            if (leave) "Najpierw wskaż, kto przejmie krąg" else "Kto ma zostać Ownerem „${community.name}”?",
            if (leave) "Ta osoba musi się zgodzić. Do tego czasu nadal jesteś Ownerem."
            else "Ta osoba musi się zgodzić. Potem zostaniesz w kręgu jako $stayRole.",
        )
        when {
            failed -> Text("Nie udało się wczytać osób. Sprawdź połączenie.", fontSize = 13.sp, color = Luxury.TextMuted)
            loaded == null -> Text("Wczytywanie…", fontSize = 13.sp, color = Luxury.TextMuted)
            people.isEmpty() -> Text("Jesteś tu sam(a) — nie ma komu przekazać roli.", fontSize = 13.sp, color = Luxury.TextMuted)
        }
        Column {
            people.forEachIndexed { index, person ->
                PersonOption(person, suggested = index == 0, on = person.id == picked) { picked = person.id }
                if (index < people.lastIndex) LuxuryDivider()
            }
        }
        val who = people.firstOrNull { it.id == picked }
        PopupButtons {
            if (who != null) {
                GoldButton("Poproś: ${who.name.substringBefore(' ')}") {
                    session.attempt(scope, outcome) {
                        requestOwnerTransfer(community.id, who.id, leave)
                        onDismiss()
                        outcome(MessageKind.SUCCESS, "Wysłano prośbę", "${who.name} zobaczy ją na Pulpicie. Do zgody nadal jesteś Ownerem.")
                    }
                }
            }
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun PersonOption(person: Member, suggested: Boolean, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, Luxury.GoldBase, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight))
        }
        PersonAvatar(person.name, person.avatar, size = 34.dp, zoom = AvatarZoom.HOLD)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(person.name, fontSize = 15.sp)
                if (suggested) {
                    Text(
                        "PROPONOWANY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Luxury.GoldText,
                        modifier = Modifier.border(1.dp, Luxury.GoldBase, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(person.role.label() + (person.joinedAt?.let { " · od ${shortDate(it)}" } ?: ""), fontSize = 12.sp, color = Luxury.TextMuted)
        }
    }
}

/** Board 16 D: the Owner alone; leaving removes the community, confirmed by typing its name. */
@Composable
fun DeleteCommunityDialog(community: CommunitySummary, onDismiss: () -> Unit, outcome: Outcome) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    val same = name.trim() == community.name.trim()
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Usunąć „${community.name}”?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        MessageBar(
            MessageKind.ERROR, "Jesteś tu jedyną osobą",
            "Opuszczenie usunie krąg razem z jego listami, planami i ustawieniami. Tego nie da się cofnąć.",
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text("Wpisz nazwę, żeby potwierdzić") },
            isError = tried && !same,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Outline),
            modifier = Modifier.fillMaxWidth(),
        )
        if (tried && !same) FieldError("Wpisz dokładnie: ${community.name}")
        PopupButtons {
            DangerButton("Usuń krąg", {
                tried = true
                if (same) {
                    session.attempt(scope, outcome) {
                        deleteCommunity(community.id, name)
                        onDismiss()
                        outcome(MessageKind.SUCCESS, "Usunięto krąg", community.name)
                    }
                }
            })
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** Board 14 B: someone who is not the Owner leaves a community. */
@Composable
fun LeaveCommunityDialog(community: CommunitySummary, onDismiss: () -> Unit, outcome: Outcome) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Opuścić „${community.name}”?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        MessageBar(
            MessageKind.WARNING, "Stracisz dostęp",
            "Do jej list, planów i Pulpitu. Wrócisz tylko z nowym kodem zaproszenia od Ownera lub Admina.",
        )
        PopupButtons {
            DangerButton("Opuść krąg", {
                session.attempt(scope, outcome) {
                    leaveCommunity(community.id)
                    onDismiss()
                    val now = (state.value as? pl.ambif.ambify.core.session.SessionState.SignedIn)?.me?.familyName
                    outcome(MessageKind.SUCCESS, "Opuszczono „${community.name}”", now?.let { "Jesteś w: $it" }.orEmpty())
                }
            })
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** Board 16 B: the Owner's open request, shown in the community switcher and in the settings. */
@Composable
fun PendingTransfer(transfer: OwnerTransfer, outcome: Outcome) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    val first = transfer.toName.substringBefore(' ')
    if (transfer.declined) {
        MessageBar(MessageKind.WARNING, "$first nie przejmuje kręgu", "Wybierz kogoś innego albo zamknij.",
            onClose = { session.attempt(scope, outcome) { cancelOwnerTransfer(transfer.communityId) } })
    } else {
        MessageBar(
            MessageKind.INFO, "Czeka na zgodę: $first",
            "Poproszono ${shortDate(transfer.createdAt)} · wygasa ${shortDate(transfer.expiresAt)}. Do tego czasu jesteś Ownerem.",
            action = "Anuluj prośbę",
            onAction = {
                session.attempt(scope, outcome) {
                    cancelOwnerTransfer(transfer.communityId)
                    outcome(MessageKind.SUCCESS, "Anulowano prośbę", "Nadal jesteś Ownerem „${transfer.communityName}”.")
                }
            },
        )
    }
}

/** Board 16 C: the request asked of me, on top of the Dashboard until I answer (or it expires). */
@Composable
fun TransferRequestCard(transfer: OwnerTransfer, outcome: Outcome) {
    val session = LocalAppSession.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun answer(accept: Boolean) {
        busy = true
        session.attempt(scope, { kind, title, text -> busy = false; outcome(kind, title, text) }) {
            answerOwnerTransfer(transfer.communityId, accept)
            if (accept) {
                switchCommunity(transfer.communityId)
                outcome(MessageKind.SUCCESS, "Jesteś Ownerem", "Krąg „${transfer.communityName}” jest teraz Twój.")
            } else {
                outcome(MessageKind.INFO, "Odrzucono prośbę", "${transfer.fromName.substringBefore(' ')} wybierze kogoś innego.")
            }
        }
    }
    LuxuryCard(contentPadding = PaddingValues(16.dp)) {
        LuxurySectionTitle("Prośba")
        Text(
            "${transfer.fromName.substringBefore(' ')} chce przekazać Ci rolę Ownera kręgu „${transfer.communityName}”" +
                if (transfer.leave) " i ją opuścić." else ".",
            fontSize = 16.sp, lineHeight = 22.sp,
        )
        Text(
            "Owner zarządza ludźmi, rolami, modułami i premium." + if (transfer.leave) " ${transfer.fromName.substringBefore(' ')} straci dostęp do tego kręgu." else "",
            fontSize = 13.sp, color = Luxury.TextMuted,
        )
        GoldButton("Przejmij", enabled = !busy) { answer(true) }
        FramedButton("Odrzuć", gold = false, modifier = Modifier.fillMaxWidth()) { if (!busy) answer(false) }
    }
}

/** The requests to show on the Dashboard: asked of me, and my own declined ones. */
fun Me.dashboardTransfers(): List<OwnerTransfer> = transfers.filter { it.forMe || it.declined }
