package pl.ambif.ambify.features.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.session.LocalAppSession
import pl.ambif.ambify.core.session.SessionState
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.modules.timetable.TimetableCache
import pl.ambif.ambify.shared.Dashboard
import pl.ambif.ambify.shared.DashboardWidget
import pl.ambif.ambify.shared.Dashboards
import pl.ambif.ambify.shared.ModuleId
import pl.ambif.ambify.shared.Recurrences
import pl.ambif.ambify.shared.WidgetSlot
import pl.ambif.ambify.shared.WidgetType
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard

/** The settings window of a widget: a new one (from the picker) or one on the Dashboard (from its gear). */
private data class Editing(val widget: DashboardWidget, val isNew: Boolean)

/**
 * Pulpit: each person's own widgets in the current community (kept on the server, with a copy on the phone).
 * Empty → "Dodaj widget"; the button then stays below the widgets. Hold a widget and drag it to move it;
 * The gear on a widget opens its settings. Widgets of a module switched off are hidden (and come back with it).
 */
@Composable
fun HomeScreen(
    organizationName: String = "",
    userName: String = "",
    onOpenModule: (ModuleId) -> Unit = {},
) {
    val session = LocalAppSession.current
    val api = session.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState()
    val modules by session.modules.collectAsState()
    val communityId = (state as? SessionState.SignedIn)?.me?.communityId.orEmpty()
    val store = remember { DashboardStore(context) }
    val enabled = modules?.enabled.orEmpty()

    var dashboard by remember(communityId) { mutableStateOf(store.load(communityId) ?: Dashboard()) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var undo by remember { mutableStateOf<Pair<String, Dashboard>?>(null) }
    var timetable by remember(communityId) { mutableStateOf<TimetableData?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val scroll = rememberScrollState()

    fun change(next: Dashboard) {
        val normalized = Dashboards.normalized(next)
        dashboard = normalized
        store.save(communityId, normalized)
        store.setPending(communityId, true)
        scope.launch {
            runCatching { api.saveDashboard(normalized) }.onSuccess { store.setPending(communityId, false) }
        }
    }

    // The server's copy, unless this phone has a change the server has not got yet: then that one goes up.
    LaunchedEffect(communityId) {
        if (communityId.isEmpty()) return@LaunchedEffect
        if (store.pending(communityId)) {
            runCatching { api.saveDashboard(dashboard) }.onSuccess { store.setPending(communityId, false) }
        } else {
            runCatching { api.dashboard() }.onSuccess { remote ->
                dashboard = remote
                store.save(communityId, remote)
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }

    val known = dashboard.widgets.filter { widget -> WidgetType.of(widget.type)?.module?.let { it in enabled } == true }
    val hidden = dashboard.widgets - known.toSet()
    val needsTimetable = ModuleId.TIMETABLE in enabled && (picker || editing != null || known.any { it.type == WidgetType.LESSONS.key })
    LaunchedEffect(communityId, needsTimetable) {
        if (needsTimetable && communityId.isNotEmpty()) timetable = loadTimetable(api, context, communityId)
    }

    /** The visible widgets in a new order; the hidden ones stay below them. */
    fun arrange(visible: List<DashboardWidget>) {
        val last = visible.maxOfOrNull { it.row } ?: -1
        change(Dashboard(visible + hidden.map { it.copy(row = last + 1 + it.row) }))
    }

    if (picker) {
        BackHandler { picker = false }
        WidgetPicker(
            enabled = enabled,
            features = modules?.features.orEmpty(),
            onDashboard = known,
            onBack = { picker = false },
            onAdd = { type ->
                editing = Editing(DashboardWidget(UUID.randomUUID().toString().take(12), type.key, Int.MAX_VALUE, if (type.half) WidgetSlot.LEFT else WidgetSlot.FULL), isNew = true)
            },
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            DashboardGrid(
                widgets = known,
                scroll = scroll,
                onMove = ::arrange,
                header = {
                    Column {
                        Text("Witaj, $userName", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(organizationName, style = MaterialTheme.typography.titleMedium, color = Luxury.GoldText)
                    }
                },
                footer = {
                    if (known.isEmpty()) {
                        LuxuryCard(contentPadding = PaddingValues(20.dp)) {
                            Text("Twój Pulpit jest pusty", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "Ułóż tu to, na co patrzysz najczęściej, np. lekcje dzieci. Każdy moduł ma swoje widgety.",
                                fontSize = 14.sp,
                                color = Luxury.TextMuted,
                            )
                            GoldButton("Dodaj widget") { picker = true }
                        }
                    } else {
                        AddWidgetButton { picker = true }
                    }
                },
            ) { widget, interactive, modifier ->
                when (WidgetType.of(widget.type)) {
                    WidgetType.LESSONS -> LessonsWidget(
                        widget = widget,
                        data = timetable,
                        now = now,
                        store = store,
                        interactive = interactive,
                        onSettings = { editing = Editing(widget, isNew = false) },
                        onOpen = { onOpenModule(ModuleId.TIMETABLE) },
                        modifier = modifier,
                    )
                    WidgetType.LIST, WidgetType.MY_TASKS, WidgetType.DEADLINES, WidgetType.ACTIVE_LISTS -> pl.ambif.ambify.features.lists.ListsWidget(
                        widget = widget,
                        interactive = interactive,
                        onSettings = { editing = Editing(widget, isNew = false) },
                        onOpen = { onOpenModule(ModuleId.LISTS) },
                        modifier = modifier,
                    )
                    null -> Unit
                }
            }
            undo?.let { (name, before) ->
                LaunchedEffect(undo) {
                    delay(6_000)
                    undo = null
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Luxury.PopupTop)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Usunięto „$name”", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Cofnij",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Luxury.GoldLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) {
                                change(before)
                                undo = null
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }

    editing?.let { open ->
        val rows = rowsOf(known)
        val index = rows.indexOfFirst { row -> row.widgets.any { it.id == open.widget.id } }
        val onMove = { up: Boolean ->
            val list = rows.toMutableList()
            val other = if (up) index - 1 else index + 1
            list[index] = rows[other].also { list[other] = rows[index] }
            arrange(list.flatMapIndexed { i, row -> row.widgets.map { it.copy(row = i) } })
        }
        val onSave = { saved: DashboardWidget ->
            if (open.isNew) {
                arrange(known + saved.copy(row = (known.maxOfOrNull { it.row } ?: -1) + 1))
                picker = false
            } else {
                arrange(known.map { if (it.id == saved.id) saved else it })
            }
            editing = null
        }
        val onDelete = {
            undo = (WidgetType.of(open.widget.type)?.label ?: "widget") to dashboard
            arrange(known.filter { it.id != open.widget.id })
            editing = null
        }
        if (WidgetType.of(open.widget.type)?.module == ModuleId.LISTS) {
            pl.ambif.ambify.features.lists.ListsWidgetSettings(
                widget = open.widget, isNew = open.isNew,
                canMoveUp = index > 0, canMoveDown = index in 0 until rows.lastIndex,
                onMove = onMove, onDismiss = { editing = null }, onSave = onSave, onDelete = onDelete,
            )
        } else LessonsSettings(
            widget = open.widget,
            isNew = open.isNew,
            data = timetable,
            now = now,
            canMoveUp = index > 0,
            canMoveDown = index in 0 until rows.lastIndex,
            onMove = onMove,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }
}

/** The outlined "Dodaj widget" below the widgets. */
@Composable
private fun AddWidgetButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Luxury.GoldBase.copy(alpha = 0.08f))
            .border(1.dp, Luxury.GoldLink, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Plus, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
        Text("  Dodaj widget", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldText)
    }
}

/**
 * This week and the next (Friday after lessons shows Monday), from the server or else from the phone's copy of
 * the timetable; fresh weeks also go to that copy.
 */
private suspend fun loadTimetable(api: ServerApi, context: Context, communityId: String): TimetableData {
    val monday = Recurrences.mondayOf(LocalDate.now())
    val weeks = mutableListOf<pl.ambif.ambify.shared.TimetableWeek>()
    var offlineSince: Long? = null
    for (week in listOf(monday, monday.plusWeeks(1))) {
        try {
            val fresh = api.timetable(week.toString())
            TimetableCache.save(context, communityId, fresh)
            weeks += fresh
        } catch (e: ServerError) {
            val saved = TimetableCache.load(context, communityId, week.toString()) ?: continue
            weeks += saved.week
            offlineSince = minOf(offlineSince ?: saved.savedAt, saved.savedAt)
        }
    }
    return TimetableData(weeks, offlineSince)
}
