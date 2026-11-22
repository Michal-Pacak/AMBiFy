package pl.ambif.ambify.features.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.lists.data.ListPeople
import pl.ambif.ambify.features.lists.data.ListReminders
import pl.ambif.ambify.features.lists.data.ListRepository
import pl.ambif.ambify.features.lists.data.ListsData
import pl.ambif.ambify.features.lists.data.ListsEvent
import pl.ambif.ambify.features.lists.data.ListsSyncWorker
import pl.ambif.ambify.features.lists.data.SyncStatus
import pl.ambif.ambify.features.lists.data.changedFields
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.features.timetable.can
import pl.ambif.ambify.shared.AccessLevel
import pl.ambif.ambify.shared.Action
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.Feature
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListIcon
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.ListAccess
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Me
import pl.ambif.ambify.shared.Member
import pl.ambif.ambify.shared.Role
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.ui.components.BubbleHost
import pl.ambif.ambify.ui.components.Bubbles
import pl.ambif.ambify.ui.components.FullScreenMessage
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageChip
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

internal val Muted = Color(0xFFA9A9B1)
internal val Faint = Color(0xFF85858D)
internal val Outline = Color(0xFF3A3A40)

/**
 * Listy (Stage 5, canvas "AMBiFy Listy", boards 1–9): shopping, tasks and deadlines of the community. Works on the
 * phone's copy (offline first); changes go to the server at once when it can be reached, else later — also in the
 * background. Messages follow the Komunikaty canvas.
 */
@Composable
fun ListsScreen(resetKey: Int = 0) {
    val session = LocalAppSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()
    val me = (sessionState as? SessionState.SignedIn)?.me ?: return
    val communityId = me.communityId ?: return
    val repo = remember(communityId) { ListRepository.of(context, api, communityId) }
    val state by repo.state.collectAsState()
    val status by repo.status.collectAsState()
    val people by repo.people.collectAsState()
    val bubbles = remember { Bubbles() }
    val lists = remember(repo) { ListsController(repo, scope, me.id) }
    val prefs = remember { ListPrefs(context) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<ListDialog?>(null) }

    LaunchedEffect(resetKey) { if (resetKey > 0) openId = null }
    // A list tapped on the Dashboard (board 19).
    val fromDashboard by ListsNavigation.open.collectAsState()
    LaunchedEffect(fromDashboard) {
        fromDashboard?.let { openId = it }
        ListsNavigation.open.value = null
    }
    BackHandler(enabled = openId != null) { openId = null }
    LaunchedEffect(repo) {
        ListsSyncWorker.schedulePeriodic(context)
        repo.refreshPeople {
            val family = api.family()
            ListPeople(family.members, api.modules().features, me.id, family.roles)
        }
    }
    // While the screen is open: at once, then every minute (every 15 s while something waits or the server is away).
    LaunchedEffect(repo) {
        while (true) {
            repo.sync()
            delay(if (repo.hasPending || repo.status.value !is SyncStatus.Synced) 15_000 else 60_000)
        }
    }
    LaunchedEffect(repo) {
        repo.events.collect { event ->
            val kind = when (event.kind) {
                ListsEvent.Kind.REFUSED, ListsEvent.Kind.CONFLICT -> MessageKind.ERROR
                ListsEvent.Kind.QUEUED -> MessageKind.INFO
                ListsEvent.Kind.RESOLVED -> MessageKind.SUCCESS
            }
            bubbles.show(kind, event.title, event.text)
        }
    }
    val view = state.view
    LaunchedEffect(view.items, people.features) {
        if (Feature.LISTS_REMINDERS in people.features) ListReminders.plan(context, view, me.id)
    }
    DisposableEffect(repo) { onDispose { if (repo.hasPending) ListsSyncWorker.syncWhenOnline(context) } }

    val ui = ListsUi(
        me = me,
        data = view,
        members = people.members,
        features = people.features,
        waitingItems = state.pending.mapNotNull { it.itemId }.toSet(),
        waitingLists = state.pending.filter { it.itemId == null }.map { it.listId }.toSet(),
        status = status,
        syncedAt = state.syncedAt,
        roles = people.roles,
        prefs = prefs,
    )
    val open = openId?.let { id -> view.lists.firstOrNull { it.id == id } }
    // An open list that went away (deleted, access taken) closes. Checked against the lists of now, not of the
    // composition that started this: an id just set (e.g. from a Dashboard widget) is not "gone".
    LaunchedEffect(openId, open) {
        val id = openId ?: return@LaunchedEffect
        if (state.syncedAt != null && state.view.lists.none { it.id == id }) openId = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (open != null) {
            ListDetail(ui, open, lists, onBack = { openId = null }, onDialog = { dialog = it }, onInfo = { kind, title, text -> bubbles.show(kind, title, text) })
        } else {
            ListsOverview(ui, onOpen = { openId = it.id }, onDialog = { dialog = it }, onRetry = { scope.launch { repo.sync() } })
        }
        BubbleHost(bubbles)
    }

    val scoped = remember(communityId) { api.forCommunity(communityId) }
    ListDialogs(
        dialog = dialog,
        ui = ui,
        lists = lists,
        onDialog = { dialog = it },
        onCreated = { openId = it },
        onClosed = { openId = null },
        onResolve = { conflictId, decision ->
            scope.launch {
                try {
                    repo.resolve(scoped, conflictId, decision)
                    bubbles.show(MessageKind.SUCCESS, "Rozstrzygnięto", "Pozostali dostaną informację o Twojej decyzji.")
                } catch (e: ServerError) {
                    bubbles.show(MessageKind.ERROR, "Nie udało się rozstrzygnąć", if (e.code == "network") "Rozstrzygnięcie wymaga połączenia z serwerem. Spróbuj, gdy wróci sieć." else e.message)
                }
            }
        },
        onSaved = { title, text -> bubbles.show(MessageKind.SUCCESS, title, text) },
    )
}

/** What the screens need to show the lists. */
internal data class ListsUi(
    val me: Me,
    val data: ListsData,
    val members: List<Member>,
    val features: List<Feature>,
    /** Items and lists with changes not sent yet ("czeka na wysłanie"). */
    val waitingItems: Set<String>,
    val waitingLists: Set<String>,
    val status: SyncStatus,
    val syncedAt: Long?,
    val roles: List<Role> = emptyList(),
    val prefs: ListPrefs,
) {
    fun member(id: String?): Member? = members.firstOrNull { it.id == id }
    fun name(id: String?): String = when (id) {
        null -> ""
        me.id -> "Ty"
        else -> member(id)?.name ?: "ktoś spoza społeczności"
    }
    val reminders: Boolean get() = Feature.LISTS_REMINDERS in features
    val tasksForOthers: Mode get() = me.permissions[Action.LISTS_TASKS_OTHERS] ?: Mode.DENIED
    fun manageable(list: ListInfo) = list.createdBy == me.id || me.can(Action.LISTS_DELETE_OTHERS)
    val canCreate: Boolean get() = me.can(Action.LISTS_CREATE)
    /** What [member] may do on [list] (board 15). */
    fun levelOf(list: ListInfo, member: Member): AccessLevel = Lists.level(
        list, member.id, member.role.id, member.role.owner,
        roleMayEdit = if (member.id == me.id) me.can(Action.LISTS_EDIT_ITEMS) else true,
    )
    fun level(list: ListInfo): AccessLevel =
        Lists.level(list, me.id, me.role?.id, me.role?.owner == true, me.can(Action.LISTS_EDIT_ITEMS))
    fun canAdd(list: ListInfo) = level(list).atLeast(AccessLevel.ADD)
    fun canCheck(list: ListInfo) = level(list).atLeast(AccessLevel.CHECK)
    /** The item a sub-item belongs to, or the item itself: its "Inny dostęp" counts for both. */
    fun topOf(item: ListItem): ListItem = item.parentId?.let { data.item(it) } ?: item
    /** On the list, narrowed by "Inny dostęp do tego zadania" (0.7.2). */
    fun itemLevel(list: ListInfo, item: ListItem): AccessLevel =
        Lists.itemLevel(list, topOf(item), me.id, me.role?.id, me.role?.owner == true, me.can(Action.LISTS_EDIT_ITEMS))
    fun canCheck(list: ListInfo, item: ListItem) = itemLevel(list, item).atLeast(AccessLevel.CHECK)
    /** Adding sub-items to [item]. */
    fun canAddTo(list: ListInfo, item: ListItem) = itemLevel(list, item).atLeast(AccessLevel.ADD)
    /** Own items with "dodaje", everyone's with "edytuje i usuwa". */
    fun canEdit(list: ListInfo, item: ListItem) = itemLevel(list, item).let { level ->
        level.atLeast(AccessLevel.EDIT_ALL) || (item.addedBy == me.id && level.atLeast(AccessLevel.ADD))
    }
    /** Premium "Podlisty" (sub-items and "Inny dostęp do tego zadania") and "Linki". */
    val subItems: Boolean get() = Feature.LISTS_SUBITEMS in features
    val links: Boolean get() = Feature.LISTS_ATTACHMENTS in features
    /** "tylko Bartek", "Bartek, Filip": who sees an item with its own access, besides who always sees everything. */
    fun whoSeesItem(list: ListInfo, item: ListItem): String? {
        if (item.access.isEmpty()) return null
        val people = members.filter { m ->
            m.id != list.createdBy && !m.role.owner &&
                Lists.itemLevel(list, item, m.id, m.role.id, m.role.owner, roleMayEdit = true).atLeast(AccessLevel.SEE)
        }
        return when (people.size) {
            0 -> "tylko twórca listy"
            1 -> "tylko " + people.single().let { if (it.id == me.id) "Ty" else it.name }
            else -> people.joinToString(", ") { if (it.id == me.id) "Ty" else it.name }
        }
    }
    fun people(list: ListInfo): List<Member> = members.filter { levelOf(list, it).atLeast(AccessLevel.SEE) }
    fun whoSees(list: ListInfo): String {
        val people = people(list)
        return if (people.size == members.size && members.isNotEmpty()) "widzi cała społeczność"
        else "widzi: " + people.joinToString(", ") { if (it.id == me.id) "Ty" else it.name }
    }
}

/** The changes a person makes: shown at once, sent at once (or later, when the server is back). */
internal class ListsController(private val repo: ListRepository, private val scope: CoroutineScope, private val me: String) {
    private fun run(kind: ListChangeKind, listId: String, itemId: String? = null, block: pl.ambif.ambify.shared.ListChange.() -> pl.ambif.ambify.shared.ListChange) {
        scope.launch {
            repo.change(kind, listId, itemId, block)
            repo.sync()
        }
    }

    fun createList(list: ListInfo): String {
        val id = UUID.randomUUID().toString()
        run(ListChangeKind.CREATE_LIST, id) { copy(list = list.copy(id = id, name = list.name.trim(), everyone = list.access.isEmpty(), createdBy = me)) }
        return id
    }

    fun updateList(list: ListInfo) = run(ListChangeKind.UPDATE_LIST, list.id) { copy(list = list) }
    fun deleteList(list: ListInfo) = run(ListChangeKind.DELETE_LIST, list.id) { this }
    fun addItem(listId: String, values: ItemValues, remember: Boolean = false, parentId: String? = null) =
        run(ListChangeKind.ADD_ITEM, listId, UUID.randomUUID().toString()) { copy(values = values, rememberUnit = remember, parentId = parentId) }

    /** A unit of the community with its forms, new or corrected (board 24); [listId]: the list it was done on. */
    fun saveUnit(listId: String, unit: ListUnit) = run(ListChangeKind.SAVE_UNIT, listId) { copy(unit = unit) }

    /** "Inny dostęp do tego zadania"; empty = as on the list. */
    fun itemAccess(item: ListItem, access: List<ListAccess>) = run(ListChangeKind.ITEM_ACCESS, item.listId, item.id) { copy(access = access) }

    fun edit(item: ListItem, values: ItemValues, remember: Boolean = false) {
        val fields = changedFields(item.values, values)
        if (fields.isEmpty() && !remember) return
        run(ListChangeKind.EDIT_ITEM, item.listId, item.id) { copy(values = values, fields = fields, baseRev = item.rev, rememberUnit = remember) }
    }

    fun check(item: ListItem, checked: Boolean) = run(ListChangeKind.CHECK_ITEM, item.listId, item.id) { copy(checked = checked) }

    /** A done thing back on the list (board 20), with [values] if they changed: no second item. */
    fun restore(item: ListItem, values: ItemValues = item.values) {
        edit(item, values)
        check(item, false)
    }

    /** "Usuń z pamięci": the product [values] of the kind of lists [listId] is (the items stay). */
    fun forget(listId: String, values: ItemValues) = run(ListChangeKind.FORGET_PRODUCT, listId) { copy(values = values) }

    /** "Zrób kopię": a new list like [list] with its things (none checked); returns the new list's id. */
    fun copyList(list: ListInfo, name: String, items: List<ListItem>): String {
        val id = createList(list.copy(name = name, archived = false))
        items.forEach { addItem(id, it.values) }
        return id
    }
    fun delete(item: ListItem) = run(ListChangeKind.DELETE_ITEM, item.listId, item.id) { this }
    fun clearChecked(listId: String) = run(ListChangeKind.CLEAR_CHECKED, listId) { this }
    fun uncheckAll(listId: String) = run(ListChangeKind.UNCHECK_ALL, listId) { this }

    /** "Połącz": [keep] gets [values] (e.g. the summed amount), [drop] goes away. */
    fun merge(keep: ListItem, drop: ListItem, values: ItemValues) {
        edit(keep, values)
        delete(drop)
    }
}

/** The popups of the module (one at a time). */
internal sealed interface ListDialog {
    data object NewList : ListDialog
    data class EditList(val list: ListInfo) : ListDialog
    /** Board 15; [isNew]: the second step of a new list (not created yet). */
    data class Access(val list: ListInfo, val isNew: Boolean) : ListDialog
    /** Board 12: my check finished the list. */
    data class Done(val list: ListInfo) : ListDialog
    /** Board 14: adding [values] while [existing] is already on the list. */
    data class Duplicate(val list: ListInfo, val existing: ListItem, val values: ItemValues, val remember: Boolean) : ListDialog
    /** Board 14: "Podwójnie?" after a sync. */
    data class Merge(val list: ListInfo, val first: ListItem, val second: ListItem) : ListDialog
    data object SortLists : ListDialog
    data class SortItems(val list: ListInfo) : ListDialog
    data class Menu(val list: ListInfo) : ListDialog
    /** Board 20: "Zamknij" in a list: finish, archive, copy, send as text, delete. */
    data class Close(val list: ListInfo) : ListDialog
    /** Boards 21–23: things added before, picked and set in two steps. */
    data class Memory(val list: ListInfo) : ListDialog
    data class DeleteList(val list: ListInfo) : ListDialog
    /** [item] null: a new one on [list]. */
    data class Item(val list: ListInfo, val item: ListItem?) : ListDialog
    data class Quantity(val list: ListInfo, val item: ListItem) : ListDialog
    data class Conflict(val list: ListInfo, val item: ListItem) : ListDialog
    /** Board 16: ⋮ of an item — sub-items, details, its own access. */
    data class ItemMenu(val list: ListInfo, val item: ListItem) : ListDialog
    data class ItemAccess(val list: ListInfo, val item: ListItem) : ListDialog
    /** Checking an item whose sub-items are not all done. */
    data class CheckAll(val list: ListInfo, val item: ListItem) : ListDialog
    /** The last sub-item done: "Odhaczyć też ‘…’?". */
    data class CheckParent(val list: ListInfo, val item: ListItem) : ListDialog
}

/** Board 1: the lists of the community, by kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListsOverview(ui: ListsUi, onOpen: (ListInfo) -> Unit, onDialog: (ListDialog) -> Unit, onRetry: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf<ListType?>(null) }
    var archive by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Listy", fontSize = 26.sp, fontWeight = FontWeight.Medium)
                Text("Społeczność: ${ui.me.familyName.orEmpty()}", fontSize = 13.sp, color = Muted)
            }
            SyncMark(ui.status)
            IconButton(onClick = { onDialog(ListDialog.SortLists) }) { Icon(AppIcons.Sort, contentDescription = "Sortuj listy", tint = Muted) }
        }
        SyncBar(ui, onRetry)
        val visible = ui.data.lists.filter { !it.archived }
        if (ui.syncedAt == null && visible.isEmpty()) {
            if (ui.status is SyncStatus.Offline || ui.status is SyncStatus.Failed) {
                FullScreenMessage(
                    MessageKind.ERROR, "Nie ma list do pokazania",
                    "Brak połączenia z serwerem, a list nie ma jeszcze w telefonie. Zapiszą się, gdy raz otworzysz je z siecią.",
                    icon = AppIcons.Offline, action = "Spróbuj ponownie", onAction = onRetry,
                )
            }
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Wszystkie", filter == null) { filter = null }
            ListType.entries.forEach { type -> Chip(typeLabel(type), filter == type) { filter = type } }
        }
        if (visible.isEmpty()) {
            FullScreenMessage(
                MessageKind.INFO, "Nie ma jeszcze list",
                if (ui.canCreate) "Załóż pierwszą: zakupy, zadania domowe albo terminy opłat." else "Gdy ktoś założy listę dla Ciebie albo dla całej społeczności, pojawi się tutaj.",
                icon = AppIcons.TaskBox,
            )
        }
        sortedLists(ui, visible.filter { filter == null || it.type == filter }, ui.prefs.listsSort()).forEach { (title, group) ->
            LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), spacing = 0.dp) {
                if (title != null) LuxurySectionTitle(title)
                group.forEachIndexed { i, list ->
                    if (i > 0) LuxuryDivider()
                    ListRow(ui, list, onOpen = { onOpen(list) }, onMenu = { if (ui.manageable(list)) onDialog(ListDialog.Menu(list)) })
                }
            }
        }
        if (ui.canCreate) GoldButton("+  Nowa lista") { onDialog(ListDialog.NewList) }
        if (visible.any { ui.manageable(it) }) {
            Text("Przytrzymaj listę: Ustawienia · Dostęp · Archiwizuj · Usuń", fontSize = 11.sp, color = Faint, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        val archived = ui.data.lists.filter { it.archived && (filter == null || it.type == filter) }
        if (archived.isNotEmpty()) {
            Text(
                (if (archive) "Ukryj archiwum" else "Archiwum: ${archived.size} · pokaż"),
                color = Luxury.GoldText, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { archive = !archive }.padding(8.dp),
            )
            if (archive) {
                LuxuryCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), spacing = 0.dp) {
                    LuxurySectionTitle("Archiwum")
                    archived.forEachIndexed { i, list ->
                        if (i > 0) LuxuryDivider()
                        ListRow(ui, list, onOpen = { onOpen(list) }, onMenu = { if (ui.manageable(list)) onDialog(ListDialog.Menu(list)) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(ui: ListsUi, list: ListInfo, onOpen: () -> Unit, onMenu: () -> Unit) {
    val items = ui.data.itemsOf(list.id)
    val conflicts = ui.data.conflicts.any { it.listId == list.id && !it.resolved }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).combinedClickable(onClick = onOpen, onLongClick = onMenu, onClickLabel = "Otwórz listę", onLongClickLabel = "Opcje listy"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Luxury.GoldBase.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(listIcon(list), contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(list.name, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Summary(ui, list, items)
            if (list.type != ListType.DEADLINES && items.isNotEmpty() && items.none { it.values.urgent }) Progress(items.count { it.checked }.toFloat() / items.size)
            if (conflicts || list.id in ui.waitingLists) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (conflicts) MessageChip(MessageKind.ERROR, "Konflikt", icon = AppIcons.Warning)
                    if (list.id in ui.waitingLists) MessageChip(MessageKind.STATUS, "Czeka na wysłanie", icon = AppIcons.Clock)
                }
            }
        }
        val people = ui.people(list)
        if (people.size < ui.members.size) {
            Row(modifier = Modifier.padding(start = 5.dp)) {
                people.take(3).forEach { PersonAvatar(it.name, it.avatar, size = 22.dp, modifier = Modifier.offset(x = (-5).dp)) }
            }
        }
        LuxuryOpenMark(size = 36.dp)
    }
}

@Composable
private fun Summary(ui: ListsUi, list: ListInfo, items: List<ListItem>) {
    val (text, warning) = summaryOf(ui, list, items)
    Text(text, fontSize = 12.sp, color = if (warning) MessageKind.WARNING.color else Muted)
}

/** "3 do kupienia · 5 kupione", "1 pilne · 2 otwarte", "najbliższy: 4.12"; true when something is urgent or late. */
internal fun summaryOf(ui: ListsUi, list: ListInfo, items: List<ListItem>): Pair<String, Boolean> {
    val open = items.filter { !it.checked }
    val urgent = open.count { it.values.urgent }
    val late = open.count { isLate(it) }
    val warn = listOfNotNull(
        if (urgent > 0) "$urgent ${plural(urgent, "pilne", "pilne", "pilnych")}" else null,
        if (late > 0) "$late po terminie" else null,
    )
    val text = when (list.type) {
        ListType.SHOPPING -> if (items.isEmpty()) "pusta" else "${open.size} ${list.look.openLabel}" + if (items.size > open.size) " · ${items.size - open.size} ${list.look.doneLabel}" else ""
        ListType.TASKS -> if (items.isEmpty()) "pusta" else (warn + ("${open.size} " + plural(open.size, "otwarte", "otwarte", "otwartych") +
            open.count { it.values.assigneeId == ui.me.id }.let { if (it > 0) " · $it dla Ciebie" else "" })).joinToString(" · ")
        ListType.DEADLINES -> {
            val today = java.time.LocalDate.now()
            val next = open.mapNotNull { item -> item.values.dueDate?.let { java.time.LocalDate.parse(it) } }.filter { !it.isBefore(today) }.minOrNull()
            if (open.isEmpty()) "nic do załatwienia"
            else (warn + listOfNotNull(next?.let { "najbliższy: ${it.dayOfMonth}.${it.monthValue}" })).joinToString(" · ")
        }
    }
    return text to warn.isNotEmpty()
}

@Composable
private fun Progress(done: Float) {
    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x14FFFFFF))) {
        Box(modifier = Modifier.fillMaxWidth(done.coerceIn(0f, 1f)).height(4.dp).background(Luxury.GoldFill))
    }
}

/** Top right of the title: how the copy stands. */
@Composable
internal fun SyncMark(status: SyncStatus) {
    val (text, color) = when (status) {
        SyncStatus.Synced -> "zsynchronizowano" to MessageKind.SUCCESS.color
        SyncStatus.Syncing -> "synchronizuję…" to Muted
        is SyncStatus.Offline -> "bez połączenia" to Luxury.GoldText
        is SyncStatus.Failed -> "błąd synchronizacji" to MessageKind.ERROR.color
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 3.dp)) {
        if (status == SyncStatus.Synced) Icon(AppIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, color = color)
    }
}

/** The bar under the title: offline (a status — the copy works) or a sync that failed (an error, with "Ponów"). */
@Composable
internal fun SyncBar(ui: ListsUi, onRetry: () -> Unit) {
    when (val status = ui.status) {
        is SyncStatus.Offline -> if (ui.syncedAt != null) {
            val waiting = status.waiting
            MessageBar(
                MessageKind.STATUS, "Bez połączenia z serwerem",
                "Działasz na kopii w telefonie." + if (waiting > 0) " ${waiting} ${plural(waiting, "zmianę", "zmiany", "zmian")} wyślę, gdy wróci sieć." else "",
                icon = AppIcons.Offline,
            )
        }
        is SyncStatus.Failed -> MessageBar(MessageKind.ERROR, "Nie udało się zsynchronizować", status.message, action = "Ponów", onAction = onRetry)
        else -> Unit
    }
}

internal fun listIcon(list: ListInfo) = when (list.type) {
    ListType.SHOPPING -> styleIcon(list.look.icon)
    ListType.TASKS -> AppIcons.TaskBox
    ListType.DEADLINES -> AppIcons.Calendar
}

internal fun styleIcon(icon: ListIcon) = when (icon) {
    ListIcon.CART -> AppIcons.Cart
    ListIcon.SUITCASE -> AppIcons.Suitcase
    ListIcon.PIN -> AppIcons.Pin
    ListIcon.STAR -> AppIcons.Star
    ListIcon.GIFT -> AppIcons.Gift
    ListIcon.LIST -> AppIcons.ListLines
}

/** Polish plural: 1 [one], 2–4 [few] (not 12–14), else [many]. */
internal fun plural(n: Int, one: String, few: String, many: String) = when {
    n == 1 -> one
    n % 10 in 2..4 && n % 100 !in 12..14 -> few
    else -> many
}
