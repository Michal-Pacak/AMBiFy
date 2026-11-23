package pl.ambif.ambify.features.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.lists.data.ListFiles
import pl.ambif.ambify.features.lists.data.ListRepository
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.ListFinish
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListPreset
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.WidgetHeight
import pl.ambif.ambify.shared.WidgetType
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.features.home.WidgetHeader
import pl.ambif.ambify.features.home.WidgetText
import pl.ambif.ambify.shared.WidgetSlot
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenButton
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

/** A list to open when the Lists module shows next (a tap on a Dashboard widget). */
object ListsNavigation {
    val open = MutableStateFlow<String?>(null)
}

/** How many items a widget of [height] shows: 1, 3 or 5 (like "Lekcje"). */
private fun countOf(height: WidgetHeight) = when (height) {
    WidgetHeight.SMALL -> 1
    WidgetHeight.MEDIUM -> 3
    WidgetHeight.LARGE -> 5
}

/** Checked within this time stays in the widget, crossed out, so a wrong tap can be undone. */
private const val RECENT_MS = 10 * 60 * 1000L

/** What the widgets need: the phone's copy of the lists (works offline) and who looks. */
@Composable
private fun rememberListsUi(): Pair<ListsUi, ListsController>? {
    val session = LocalAppSession.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by session.state.collectAsState()
    val me = (signedIn as? SessionState.SignedIn)?.me ?: return null
    val communityId = me.communityId ?: return null
    val repo = remember(communityId) { ListRepository.of(context, session.api, communityId) }
    val state by repo.state.collectAsState()
    val status by repo.status.collectAsState()
    val people by repo.people.collectAsState()
    val prefs = remember { ListPrefs(context) }
    val controller = remember(repo) { ListsController(repo, scope, me.id, ListFiles.of(context, session.api, communityId)) }
    LaunchedEffect(repo) { controller.syncAll() }
    val ui = ListsUi(
        me = me, data = state.view, members = people.members, features = people.features,
        waitingItems = state.pending.mapNotNull { it.itemId }.toSet(), waitingLists = emptySet(),
        status = status, syncedAt = state.syncedAt, roles = people.roles, prefs = prefs,
    )
    return ui to controller
}

/** Board 19: "Lista", "Moje zadania" and "Najbliższe terminy" on the Dashboard. */
@Composable
fun ListsWidget(
    widget: DashboardWidget,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (ui, lists) = rememberListsUi() ?: return
    val open: (String?) -> Unit = { id -> ListsNavigation.open.value = id; onOpen() }
    when (WidgetType.of(widget.type)) {
        WidgetType.LIST -> OneList(ui, lists, widget, interactive, onSettings, open, modifier)
        WidgetType.MY_TASKS -> MyTasks(ui, lists, widget, interactive, onSettings, open, modifier)
        WidgetType.ACTIVE_LISTS -> ActiveLists(ui, widget, interactive, onSettings, open, modifier)
        WidgetType.DEADLINES -> NextDeadlines(ui, widget, interactive, onSettings, open, modifier)
        else -> Unit
    }
}

@Composable
private fun WidgetCard(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    note: String?,
    interactive: Boolean,
    onSettings: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    noteColor: Color = MessageKind.SUCCESS.color,
    content: @Composable () -> Unit,
) {
    LuxuryCard(modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 10.dp), spacing = 0.dp) {
        WidgetHeader(
            module = "Listy", name = name, interactive = interactive, onSettings = onSettings, onOpen = onOpen, openLabel = "Otwórz: $name",
            leading = icon?.let { i -> { Icon(i, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(18.dp)) } },
            note = note?.let { n -> { Text(n, fontSize = 12.sp, color = noteColor, maxLines = 1, modifier = Modifier.padding(top = 2.dp)) } },
        )
        Column(modifier = Modifier.padding(end = 10.dp)) { content() }
    }
}

/**
 * A row of a widget, like a lesson in "Lekcje": the box (only for who may check) and the name. [trailing] (amount,
 * date, PILNE) stands right of the name in a whole widget, under it in a half one so the name has the width.
 * The name itself does not answer a touch: the widget opens from its name at the top.
 */
@Composable
private fun WidgetRow(
    item: ListItem,
    square: Boolean,
    canCheck: Boolean,
    interactive: Boolean,
    half: Boolean,
    onCheck: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).alpha(if (item.checked) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canCheck) {
            val shape = if (square) RoundedCornerShape(5.dp) else CircleShape
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .clickable(enabled = interactive && !item.pending, onClickLabel = if (item.checked) "Cofnij: ${item.values.name}" else "Odhacz: ${item.values.name}", role = Role.Checkbox, onClick = onCheck),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier.size(18.dp).clip(shape).then(if (item.checked) Modifier.background(Luxury.GoldFill) else Modifier.border(1.5.dp, Luxury.GoldBase, shape)),
                    contentAlignment = Alignment.Center,
                ) { if (item.checked) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.Surface, modifier = Modifier.size(12.dp)) }
            }
        }
        if (half) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(nameText(item.values, item.checked), fontSize = WidgetText.Item, maxLines = 2, overflow = TextOverflow.Ellipsis)
                trailing()
            }
        } else {
            Text(nameText(item.values, item.checked), fontSize = WidgetText.Item, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun Pill(text: String, color: Color = MessageKind.WARNING.color) {
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MoreText(more: Int) {
    Text("+$more więcej", fontSize = 13.sp, color = Luxury.GoldText, modifier = Modifier.padding(start = 36.dp, top = 6.dp, bottom = 2.dp))
}

@Composable
private fun Faded(text: String) {
    Text(text, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
}

// --- "Lista": one chosen list ---

@Composable
private fun OneList(ui: ListsUi, lists: ListsController, widget: DashboardWidget, interactive: Boolean, onSettings: () -> Unit, onOpen: (String?) -> Unit, modifier: Modifier) {
    val half = widget.slot != WidgetSlot.FULL
    val list = ui.data.lists.firstOrNull { it.id == widget.people.firstOrNull() }
    if (list == null) {
        WidgetCard("Wybierz listę", AppIcons.ListLines, null, interactive, onSettings, { onOpen(null) }, modifier) {
            Faded(if (ui.syncedAt == null) "Wczytywanie list…" else "Tej listy już nie ma albo nie masz do niej dostępu. Wybierz inną w ustawieniach (koło zębate).")
        }
        return
    }
    // Sub-items stay in the list itself (board 16); the widget shows the items.
    val items = ui.data.itemsOf(list.id).filter { it.parentId == null }
    val now = System.currentTimeMillis()
    val open = items.filter { !it.checked }.let { if (list.type == ListType.SHOPPING) it.sortedBy { i -> i.addedAt } else it.sortedWith(dateOrder()) }
    val recent = items.filter { it.checked && (it.checkedAt ?: 0) > now - RECENT_MS }.sortedByDescending { it.checkedAt }
    val count = countOf(widget.height)
    val shown = (open + recent).take(count)
    val canCheck = ui.canCheck(list)
    // What is left matters more than what is done (Michał, 23.11): "5 do kupienia", "3 do spakowania".
    val note = if (list.type == ListType.SHOPPING && open.isNotEmpty()) "${open.size} ${list.look.openLabel}" else null
    WidgetCard(list.name, listIcon(list), note, interactive, onSettings, { onOpen(list.id) }, modifier, noteColor = Luxury.GoldText) {
        when {
            items.isEmpty() -> Faded("Lista jest pusta.")
            open.isEmpty() && list.type == ListType.SHOPPING && list.look.finish == ListFinish.RESET -> {
                Faded(if (list.look.preset == ListPreset.PACKING) "Wszystko spakowane." else "Wszystko odhaczone.")
                if (canCheck && interactive) FramedButton("Odznacz wszystko", modifier = Modifier.fillMaxWidth()) { lists.uncheckAll(list.id) }
            }
            open.isEmpty() && recent.isEmpty() -> Faded(if (list.type == ListType.SHOPPING && list.look.preset == ListPreset.SHOPPING) "Wszystko kupione." else "Wszystko zrobione.")
            else -> shown.forEachIndexed { i, item ->
                if (i > 0) LuxuryDivider()
                WidgetRow(item, square = list.type == ListType.SHOPPING, canCheck = canCheck, interactive = interactive, half = half,
                    onCheck = { lists.check(item, !item.checked) }) {
                    when {
                        item.checked -> Text("przed chwilą", fontSize = WidgetText.Small, color = Muted)
                        item.values.urgent -> Pill("PILNE")
                        list.type == ListType.SHOPPING && list.look.quantity -> Lists.quantityText(item.values, ui.data.allUnits).takeIf { it.isNotEmpty() }?.let {
                            Text(it, fontSize = 12.sp, color = Luxury.GoldText, maxLines = 1, modifier = Modifier.padding(top = if (half) 2.dp else 0.dp).clip(RoundedCornerShape(8.dp)).background(Luxury.GoldBase.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        else -> item.values.dueDate?.let { Text(shortDate(LocalDate.parse(it)), fontSize = WidgetText.Small, color = if (isLate(item)) MessageKind.ERROR.color else Luxury.GoldText) }
                    }
                }
            }
        }
        val more = open.size + recent.size - shown.size
        if (more > 0) MoreText(more)
    }
}

// --- "Moje zadania" (half) ---

@Composable
private fun MyTasks(ui: ListsUi, lists: ListsController, widget: DashboardWidget, interactive: Boolean, onSettings: () -> Unit, onOpen: (String?) -> Unit, modifier: Modifier) {
    val half = widget.slot != WidgetSlot.FULL
    val taskLists = ui.data.lists.filter { it.type == ListType.TASKS && !it.archived }.associateBy { it.id }
    val now = System.currentTimeMillis()
    val mine = ui.data.items.filter { it.listId in taskLists && !it.cleared && it.values.assigneeId == ui.me.id }
    val open = mine.filter { !it.checked }.sortedWith(dateOrder())
    val recent = mine.filter { it.checked && (it.checkedAt ?: 0) > now - RECENT_MS }
    val shown = (open + recent).take(countOf(widget.height))
    WidgetCard("Moje zadania", AppIcons.ListLines, if (open.isEmpty()) null else "${open.size} do zrobienia", interactive, onSettings, { onOpen(null) }, modifier, noteColor = Luxury.GoldText) {
        if (open.isEmpty() && recent.isEmpty()) Faded(if (ui.syncedAt == null) "Wczytywanie…" else "Nic dla Ciebie.")
        shown.forEachIndexed { i, item ->
            if (i > 0) LuxuryDivider()
            val list = taskLists.getValue(item.listId)
            WidgetRow(item, square = false, canCheck = ui.canCheck(list), interactive = interactive, half = half,
                onCheck = { lists.check(item, !item.checked) }) {
                when {
                    item.values.urgent && !item.checked -> Pill("PILNE")
                    else -> item.values.dueDate?.let { Text(shortDate(LocalDate.parse(it)), fontSize = WidgetText.Small, color = if (isLate(item)) MessageKind.ERROR.color else Luxury.GoldText) }
                }
            }
        }
        val more = open.size + recent.size - shown.size
        if (more > 0) MoreText(more)
    }
}

// --- "Najbliższe terminy" (half) ---

@Composable
private fun NextDeadlines(ui: ListsUi, widget: DashboardWidget, interactive: Boolean, onSettings: () -> Unit, onOpen: (String?) -> Unit, modifier: Modifier) {
    val half = widget.slot != WidgetSlot.FULL
    val ids = ui.data.lists.filter { it.type == ListType.DEADLINES && !it.archived }.map { it.id }.toSet()
    val open = ui.data.items.filter { it.listId in ids && !it.cleared && !it.checked }.sortedWith(dateOrder())
    val shown = open.take(countOf(widget.height))
    val today = LocalDate.now()
    WidgetCard("Najbliższe terminy", AppIcons.Calendar, if (open.isEmpty()) null else "${open.size} otwarte", interactive, onSettings, { onOpen(null) }, modifier, noteColor = Luxury.GoldText) {
        if (open.isEmpty()) Faded(if (ui.syncedAt == null) "Wczytywanie…" else "Nic nie czeka.")
        shown.forEachIndexed { i, item ->
            if (i > 0) LuxuryDivider()
            val late = isLate(item, today)
            WidgetRow(item, square = false, canCheck = false, interactive = interactive, half = half, onCheck = {}) {
                when {
                    item.values.urgent -> Pill("PILNE")
                    late -> Text(shortDate(LocalDate.parse(item.values.dueDate), today), fontSize = WidgetText.Small, color = MessageKind.ERROR.color)
                    else -> item.values.dueDate?.let { Text(shortDate(LocalDate.parse(it)), fontSize = WidgetText.Small, color = Luxury.GoldText) }
                }
            }
        }
        if (open.size > shown.size) MoreText(open.size - shown.size)
    }
}

// --- "Aktywne listy": the kinds folded, a kind opens to its lists ---

/** Height of one row of "Aktywne listy": always at least 3 of them; it grows as kinds open (no size to choose). */
private val KindRow = 48.dp

@Composable
private fun ActiveLists(ui: ListsUi, widget: DashboardWidget, interactive: Boolean, onSettings: () -> Unit, onOpen: (String?) -> Unit, modifier: Modifier) {
    val active = ui.data.lists.filter { !it.archived && ui.level(it).atLeast(pl.ambif.ambify.shared.AccessLevel.SEE) }
    val kinds = ListType.entries.mapNotNull { type -> active.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { type to it } }
    val note = if (active.isEmpty()) null else "${active.size} ${plural(active.size, "lista", "listy", "list")}"
    WidgetCard("Aktywne listy", AppIcons.ListLines, note, interactive, onSettings, { onOpen(null) }, modifier, noteColor = Luxury.GoldText) {
        if (active.isEmpty()) {
            Faded(if (ui.syncedAt == null) "Wczytywanie list…" else "Nie ma jeszcze list. Załóż pierwszą w module Listy.")
            return@WidgetCard
        }
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = KindRow * 3)) {
            kinds.forEachIndexed { i, (type, inKind) ->
                if (i > 0) LuxuryDivider()
                val opened = ui.prefs.widgetKindOpen(widget.id, type)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = KindRow).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = interactive, onClickLabel = if (opened) "Zwiń" else "Rozwiń", role = Role.Button) { ui.prefs.setWidgetKindOpen(widget.id, type, !opened) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ExpandMore, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp).rotate(if (opened) 0f else -90f))
                    Spacer(Modifier.width(8.dp))
                    Text("${typeLabel(type)} (${inKind.size})", fontSize = WidgetText.Item, fontWeight = FontWeight.Medium, color = Luxury.Text, modifier = Modifier.weight(1f))
                }
                if (opened) kindLists(ui, inKind).forEach { (list, open) ->
                    LuxuryDivider()
                    ActiveListRow(list, open, interactive) { onOpen(list.id) }
                }
            }
        }
    }
}

/** The lists of a kind with how many things are open: urgent or late first, then the recently changed, all done last. */
private fun kindLists(ui: ListsUi, lists: List<ListInfo>): List<Pair<ListInfo, Pair<Int, Boolean>>> =
    lists.map { list ->
        val items = ui.data.itemsOf(list.id)
        val open = items.filter { !it.checked }
        val warning = open.any { it.values.urgent || isLate(it) }
        Triple(list, open.size to warning, maxOf(list.rev, items.maxOfOrNull { it.rev } ?: 0))
    }.sortedWith(compareBy({ !it.second.second }, { it.second.first == 0 }, { -it.third }))
        .map { it.first to it.second }

/** One line: the icon, the name and in brackets how many things are open ("Biedronka — tydzień (3)"). */
@Composable
private fun ActiveListRow(list: ListInfo, open: Pair<Int, Boolean>, interactive: Boolean, onOpen: () -> Unit) {
    val (count, warning) = open
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = KindRow).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = interactive, onClickLabel = "Otwórz: ${list.name}", role = Role.Button, onClick = onOpen)
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(listIcon(list), contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(list.name, fontSize = WidgetText.Item, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (count == 0) Muted else Luxury.Text, modifier = Modifier.weight(1f, fill = false))
        Text(" ($count)", fontSize = WidgetText.Item, maxLines = 1, color = if (warning) MessageKind.WARNING.color else Muted)
    }
}

/** "dziś", "jutro", "sob." (this week), "12.11"; after the date "−8 dni" in every widget (like "Najbliższe terminy"). */
private fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days < 0 -> "−${-days} dni"
        days == 0L -> "dziś"
        days == 1L -> "jutro"
        days in 2..6 -> listOf("pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz.")[date.dayOfWeek.value - 1]
        else -> "${date.dayOfMonth}.${date.monthValue}"
    }
}

// --- settings (board 19, "ustawienia widgetu") ---

/**
 * Settings of a Lists widget: for "Lista" which list (each widget its own), for all the size; for a widget already on
 * the Dashboard also moving it and removing it.
 */
@Composable
fun ListsWidgetSettings(
    widget: DashboardWidget,
    isNew: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (DashboardWidget) -> Unit,
    onDelete: () -> Unit,
) {
    val (ui, _) = rememberListsUi() ?: return
    val type = WidgetType.of(widget.type) ?: return
    val all = ui.data.lists.filter { !it.archived && ui.level(it).atLeast(pl.ambif.ambify.shared.AccessLevel.SEE) }
    var listId by remember(widget.id) { mutableStateOf(widget.people.firstOrNull() ?: all.firstOrNull()?.id) }
    var height by remember(widget.id) { mutableStateOf(widget.height) }
    var half by remember(widget.id) { mutableStateOf(widget.slot != pl.ambif.ambify.shared.WidgetSlot.FULL) }
    val draft = widget.copy(
        people = if (type == WidgetType.LIST) listOfNotNull(listId) else emptyList(), height = height,
        slot = pl.ambif.ambify.features.home.slotFor(widget, half),
    )
    LuxuryPopup(onDismiss = onDismiss) {
        Column {
            Text(if (isNew) "Dodaj: „${type.label}”" else "Ustawienia: „${type.label}”", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Listy", fontSize = 13.sp, color = Luxury.TextMuted)
        }
        if (type == WidgetType.LIST) {
            LuxurySectionTitle("Która lista?")
            Text("Widget można dodać kilka razy — każdy z inną listą.", fontSize = 12.sp, color = Luxury.TextMuted)
            if (all.isEmpty()) Faded("Nie ma jeszcze list. Załóż pierwszą w module Listy.")
            Column {
                all.forEachIndexed { i, list ->
                    if (i > 0) LuxuryDivider()
                    ListChoice(list, list.id == listId) { listId = list.id }
                }
            }
        }
        pl.ambif.ambify.features.home.WidthSetting(half) { half = it }
        if (type == WidgetType.ACTIVE_LISTS) {
            Text("Wielkość dopasowuje się sama: typy list, a po rozwinięciu typu także jego listy.", fontSize = 12.sp, color = Luxury.TextMuted)
        } else {
            LuxurySectionTitle("Wielkość", modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, Outline, RoundedCornerShape(24.dp)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                WidgetHeight.entries.forEach { option ->
                    val on = option == height
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).clip(RoundedCornerShape(21.dp))
                            .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier)
                            .clickable(role = Role.RadioButton) { height = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${option.label} · ${countOf(option)}", fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, color = if (on) Luxury.Surface else Luxury.TextMuted)
                    }
                }
            }
            Text("Mała: 1 pozycja, średnia: 3, duża: 5.", fontSize = 12.sp, color = Luxury.TextMuted)
        }
        if (!isNew) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LuxurySectionTitle("Przesuń", modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
                    Icon(AppIcons.Up, contentDescription = "Przesuń w górę", tint = if (canMoveUp) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
                IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
                    Icon(AppIcons.ExpandMore, contentDescription = "Przesuń w dół", tint = if (canMoveDown) Luxury.GoldText else Luxury.Disabled.copy(alpha = 0.4f))
                }
            }
        }
        val ready = type != WidgetType.LIST || listId != null
        LuxurySectionTitle("Podgląd", modifier = Modifier.padding(top = 4.dp))
        pl.ambif.ambify.features.home.WidgetPreview(half) {
            if (ready) ListsWidget(draft, interactive = false, onSettings = {}, onOpen = {})
            else Faded("Wybierz listę, żeby zobaczyć podgląd.")
        }
        if (isNew) {
            GoldButton("Dodaj na pulpit", enabled = ready) { onSave(draft) }
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        } else {
            GoldButton("Zapisz", enabled = ready) { onSave(draft) }
            DangerButton("Usuń z pulpitu", onDelete)
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ListChoice(list: ListInfo, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.6.dp, Luxury.GoldBase, RoundedCornerShape(6.dp))),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.Surface, modifier = Modifier.size(13.dp)) }
        Text(list.name, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when (list.type) {
                ListType.SHOPPING -> "rzeczy"
                ListType.TASKS -> "zadania"
                ListType.DEADLINES -> "terminy"
            },
            fontSize = 12.sp, color = Muted,
        )
    }
}
