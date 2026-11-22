package pl.ambif.ambify.features.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.shared.AccessLevel
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListFinish
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListPreset
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageChip
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryCard
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

private val CardPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)

/** What the rows of one list need: who looks, how to check (asks "co z listą?" at 100%), the popups. */
private class Detail(
    val ui: ListsUi,
    val list: ListInfo,
    val lists: ListsController,
    val onDialog: (ListDialog) -> Unit,
    val onCheck: (ListItem, Boolean) -> Unit,
    val items: List<ListItem>,
    val onInfo: (MessageKind, String, String) -> Unit,
) {
    val sort: ItemSort get() = ui.prefs.itemSort(list)
}

/** Boards 2–4 and 11–17: one list — of things (by its style), tasks (for whom) or deadlines (by date). */
@Composable
internal fun ListDetail(
    ui: ListsUi,
    list: ListInfo,
    lists: ListsController,
    onBack: () -> Unit,
    onDialog: (ListDialog) -> Unit,
    onInfo: (MessageKind, String, String) -> Unit,
) {
    val all = ui.data.itemsOf(list.id)
    // Sub-items are shown under their item (board 16); one whose item is gone is shown on its own.
    val items = all.filter { item -> item.parentId == null || all.none { it.id == item.parentId } }
    val onCheck: (ListItem, Boolean) -> Unit = check@{ item, checked ->
        if (checked && ui.data.subItemsOf(item.id).any { !it.checked }) {
            onDialog(ListDialog.CheckAll(list, item))
            return@check
        }
        lists.check(item, checked)
        if (!checked && list.type == ListType.SHOPPING) onInfo(MessageKind.SUCCESS, "Wróciło na listę", item.values.name)
        val after = all.map { if (it.id == item.id) it.copy(checked = checked) else it }
        val parent = item.parentId?.let { id -> after.firstOrNull { it.id == id } }
        if (checked && parent != null && !parent.checked && after.filter { it.parentId == parent.id }.all { it.checked } && ui.canCheck(list, parent)) {
            onDialog(ListDialog.CheckParent(list, parent))
            return@check
        }
        if (checked && allDone(after) && !ui.prefs.neverAskDone(list.id)) {
            if (ui.manageable(list)) {
                if (after.maxOf { it.addedAt } > ui.prefs.doneAnswered(list.id)) onDialog(ListDialog.Done(list))
            } else {
                onInfo(MessageKind.SUCCESS, "Lista zrobiona", "„${list.name}” — wszystko odhaczone.")
            }
        }
    }
    val detail = Detail(ui, list, lists, onDialog, onCheck, items, onInfo)
    val level = ui.level(list)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Wróć", tint = Luxury.GoldText) }
            Column(modifier = Modifier.weight(1f)) {
                Text(list.name, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${kindLabel(list)} · ${ui.whoSees(list)}" + if (list.archived) " · w archiwum" else "", fontSize = 13.sp, color = Muted, maxLines = 2)
            }
            IconButton(onClick = { onDialog(ListDialog.SortItems(list)) }) { Icon(AppIcons.Sort, contentDescription = "Sortuj", tint = Muted) }
            CloseButton { onDialog(ListDialog.Close(list)) }
            if (ui.manageable(list)) {
                IconButton(onClick = { onDialog(ListDialog.Menu(list)) }) { Icon(AppIcons.More, contentDescription = "Opcje listy", tint = Muted) }
            }
        }
        SyncBar(ui, onRetry = {})
        if (!level.atLeast(AccessLevel.ADD)) {
            MessageBar(MessageKind.INFO, "Na tej liście: ${level.label}", "Dostęp ustala twórca listy (${ui.name(list.createdBy)}).", icon = AppIcons.Lock)
        }
        when (list.type) {
            ListType.SHOPPING -> Things(detail)
            ListType.TASKS -> Tasks(detail)
            ListType.DEADLINES -> Deadlines(detail)
        }
    }
}

/** "Lista rzeczy · Zakupy", "Zadania". */
internal fun kindLabel(list: ListInfo) =
    if (list.type == ListType.SHOPPING) "Lista rzeczy · ${list.look.preset.label}" else list.type.label

/**
 * A new item, unless the same thing is on the list already: an open one → "Jest już na liście" (board 14) decides;
 * a done one (e.g. in "Kupione") comes back with the new amount instead of a second item (board 20).
 */
internal fun addOrAsk(
    ui: ListsUi, list: ListInfo, lists: ListsController, values: ItemValues, remember: Boolean,
    onDialog: (ListDialog) -> Unit, onInfo: (String, String) -> Unit,
) {
    val items = ui.data.itemsOf(list.id).filter { it.parentId == null }
    val open = items.firstOrNull { !it.checked && Lists.sameThing(it.values, values) }
    val done = items.firstOrNull { it.checked && Lists.sameThing(it.values, values) }
    when {
        open != null -> onDialog(ListDialog.Duplicate(list, open, values, remember))
        done != null && ui.canCheck(list) && (ui.canEdit(list, done) || values.quantity == null) -> {
            val back = restoredValues(done.values, values)
            lists.restore(done, back)
            val amount = if (list.type == ListType.SHOPPING && list.look.quantity) Lists.quantityText(back, ui.data.allUnits) else ""
            onInfo("Wróciło na listę", nameOf(back) + if (amount.isNotEmpty()) " ($amount)" else "")
        }
        else -> lists.addItem(list.id, values, remember)
    }
}

/** "Mleko 3,2%". */
internal fun nameOf(values: ItemValues) = values.name + if (values.variant.isNotBlank()) " ${values.variant}" else ""

/** "+ Dodaj" of tasks and deadlines: things added before first (board 23), a new one when there are none. */
private fun addFromMemory(d: Detail) {
    if (d.ui.data.memoryOf(Lists.scopeOf(d.list)).isEmpty()) d.onDialog(ListDialog.Item(d.list, null)) else d.onDialog(ListDialog.Memory(d.list))
}

/** Board 20: "Zamknij ⌄" in the title of a list. */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Luxury.GoldLink, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Zamknij listę: co z nią zrobić", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Zamknij", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldText)
        Icon(AppIcons.ExpandMore, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(14.dp))
    }
}

// --- lists of things (shopping, packing, own kind) ---

@Composable
private fun Things(d: Detail) {
    val list = d.list
    val ui = d.ui
    val look = list.look
    var draft by rememberSaveable(list.id) { mutableStateOf("") }
    val doneOpen = ui.prefs.doneOpen(list.id)
    fun add() {
        val text = draft.trim()
        // "+" with nothing typed: things added before (board 21).
        if (text.isEmpty()) { d.onDialog(ListDialog.Memory(list)); return }
        val values = if (look.quantity) Lists.quickAdd(text, ui.data.allUnits) { key -> ui.data.products.firstOrNull { it.product == key } }
        else ItemValues(name = text.replaceFirstChar { it.uppercase() })
        addOrAsk(ui, list, d.lists, values, false, d.onDialog) { title, info -> d.onInfo(MessageKind.SUCCESS, title, info) }
        draft = ""
    }
    if (ui.canAdd(list)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(addPlaceholder(list), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Outline),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Luxury.GoldFill).clickable(onClickLabel = "Dodaj pozycję", role = Role.Button) { add() },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Plus, contentDescription = if (draft.isBlank()) "Dodaj z pamięci" else "Dodaj pozycję", tint = Luxury.Surface) }
        }
    }
    val todo = d.items.filter { !it.checked }
    val done = d.items.filter { it.checked }
    LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
        LuxurySectionTitle("${look.openLabel.cap()} · ${todo.size}")
        if (todo.isEmpty()) EmptyLine(if (d.items.isEmpty()) "Lista jest pusta. Dopisz coś powyżej." else "Wszystko odhaczone.")
        Groups(d, todo) { item ->
            ItemRow(d, item, square = true) {
                duplicateOf(ui.prefs, todo, item)?.takeIf { ui.canEdit(list, item) && ui.canEdit(list, it) }?.let { other ->
                    MessageChip(MessageKind.INFO, "Podwójnie?") { d.onDialog(ListDialog.Merge(list, item, other)) }
                }
                if (look.quantity) {
                    QuantityChip(Lists.quantityText(item.values, ui.data.allUnits), enabled = ui.canEdit(list, item)) { d.onDialog(ListDialog.Quantity(list, item)) }
                }
                ui.member(item.addedBy)?.let { PersonAvatar(it.name, it.avatar, size = 22.dp) }
            }
        }
    }
    if (done.isNotEmpty()) {
        LuxuryCard(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), spacing = 0.dp) {
            Toggle("${look.doneLabel.cap()} · ${done.size}", doneOpen, d.sort.label.takeIf { d.sort != ItemSort.ADDED }) { ui.prefs.setDoneOpen(list.id, !doneOpen) }
            if (doneOpen) {
                LuxuryDivider()
                // Sorted like the whole list ("Sortuj"); tapping the box puts a thing back (board 20).
                Groups(d, done) { item ->
                    ItemRow(d, item, square = true) {
                        Text("${ui.name(item.checkedBy).ifEmpty { "?" }} · ${time(item.checkedAt)}", fontSize = 11.sp, color = Muted)
                    }
                }
            }
        }
        if (ui.canCheck(list)) {
            FramedButton(finishLabel(list), modifier = Modifier.fillMaxWidth()) {
                if (look.finish == ListFinish.RESET) d.lists.uncheckAll(list.id) else d.lists.clearChecked(list.id)
            }
        }
    }
    if (ui.canAdd(list)) {
        Text(
            "Szczegóły pozycji (rodzaj, notatka) — stuknij jej nazwę",
            fontSize = 11.sp, color = Faint, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
    }
}

/** The quick-add field says what is added only where the list's kind tells it (board 18). */
private fun addPlaceholder(list: ListInfo) = when (list.look.preset) {
    ListPreset.SHOPPING -> "Dodaj produkt: np. mleko 2"
    ListPreset.PACKING -> "Dodaj rzecz: np. ręcznik 2"
    ListPreset.CUSTOM -> if (list.look.quantity) "Dodaj pozycję: np. … 2" else "Dodaj pozycję"
}

internal fun finishLabel(list: ListInfo) = when {
    list.look.finish == ListFinish.RESET && list.look.preset == ListPreset.PACKING -> "Odznacz wszystko (nowy wyjazd)"
    list.look.finish == ListFinish.RESET -> "Odznacz wszystko"
    else -> "Wyczyść ${list.look.doneLabel}"
}

@Composable
private fun Toggle(title: String, open: Boolean, note: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (open) "Zwiń" else "Rozwiń", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(if (open) AppIcons.ExpandMore else AppIcons.Forward, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(14.dp))
        LuxurySectionTitle(title)
        note?.let {
            Box(modifier = Modifier.weight(1f))
            Text(it, fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun QuantityChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    if (text.isEmpty() && !enabled) return
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .then(if (text.isEmpty()) Modifier.border(1.dp, Outline, shape) else Modifier.background(Luxury.GoldBase.copy(alpha = 0.12f)))
            .clickable(enabled = enabled, onClickLabel = "Ilość i jednostka", onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text.ifEmpty { "+ ilość" }, fontSize = 12.sp, color = if (text.isEmpty()) Faint else Luxury.GoldText, maxLines = 1) }
}

// --- tasks ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tasks(d: Detail) {
    val ui = d.ui
    var filter by rememberSaveable(d.list.id) { mutableStateOf(0) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Wszystkie", "Moje", "Bez osoby").forEachIndexed { i, label -> Chip(label, filter == i) { filter = i } }
    }
    val shown = d.items.filter {
        when (filter) {
            1 -> it.values.assigneeId == ui.me.id
            2 -> it.values.assigneeId == null
            else -> true
        }
    }
    val urgent = shown.filter { !it.checked && it.values.urgent }
    val open = shown.filter { !it.checked && !it.values.urgent }
    val done = shown.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 }
    if (urgent.isNotEmpty()) {
        LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
            LuxurySectionTitle("Pilne · ${urgent.size}", color = MessageKind.WARNING.color)
            Groups(d, urgent) { item -> TaskRow(d, item) }
        }
    }
    LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
        LuxurySectionTitle("Otwarte · ${open.size}")
        if (open.isEmpty()) EmptyLine("Nic do zrobienia.")
        Groups(d, open) { item -> TaskRow(d, item) }
    }
    if (done.isNotEmpty()) {
        LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
            LuxurySectionTitle("Zrobione · ${done.size}")
            done.forEachIndexed { i, item ->
                if (i > 0) LuxuryDivider()
                ItemRow(d, item, square = false, meta = "zrobił(a): ${ui.name(item.checkedBy)} · ${time(item.checkedAt)}") {}
                SubList(d, item)
            }
        }
    }
    if (ui.canAdd(d.list)) GoldButton("+  Dodaj zadanie") { addFromMemory(d) }
}

@Composable
private fun TaskRow(d: Detail, item: ListItem) {
    ItemRow(d, item, square = false, meta = taskMeta(d.ui, item), urgent = item.values.urgent) {
        if (item.pending) MessageChip(MessageKind.INFO, "czeka na zatwierdzenie")
        else d.ui.member(item.values.assigneeId)?.let { PersonAvatar(it.name, it.avatar, size = 26.dp) }
    }
}

private fun taskMeta(ui: ListsUi, item: ListItem): String {
    val parts = mutableListOf<String>()
    item.values.dueDate?.let { parts += dateWords(LocalDate.parse(it)) }
    if (item.values.recurrence.frequency != Frequency.NONE) parts += recurrenceWords(item)
    if (item.pending) parts += "dodał(a) ${ui.name(item.addedBy)} dla: ${ui.name(item.values.assigneeId)}"
    if (parts.isEmpty()) parts += if (item.values.urgent) "bez daty" else "bez terminu"
    item.lastDoneAt?.let { parts += "ostatnio: ${ui.name(item.lastDoneBy)}, ${time(it)}" }
    return parts.joinToString(" · ")
}

// --- deadlines ---

@Composable
private fun Deadlines(d: Detail) {
    var showDone by rememberSaveable(d.list.id) { mutableStateOf(false) }
    val today = LocalDate.now()
    val open = d.items.filter { !it.checked }
    val urgent = open.filter { it.values.urgent }
    val late = open.filter { isLate(it, today) }
    val next = open - late.toSet() - urgent.toSet()
    val done = d.items.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 }
    if (urgent.isNotEmpty()) {
        LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
            LuxurySectionTitle("Pilne · ${urgent.size}", color = MessageKind.WARNING.color)
            Groups(d, urgent) { item ->
                ItemRow(d, item, square = false, meta = listOfNotNull("bez daty", item.values.note.takeIf { it.isNotBlank() }?.lines()?.first()?.take(40)).joinToString(" · "), urgent = true) {}
            }
        }
    }
    if (late.isNotEmpty()) {
        LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
            LuxurySectionTitle("Po terminie", color = MessageKind.WARNING.color)
            Groups(d, late) { item -> DeadlineRow(d, item, today, late = true) }
        }
    }
    LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
        LuxurySectionTitle("Najbliższe")
        if (next.isEmpty()) EmptyLine("Nic nie czeka. Dodaj termin opłaty, przeglądu albo ważności dokumentu.")
        Groups(d, next) { item -> DeadlineRow(d, item, today, late = false) }
    }
    if (done.isNotEmpty()) {
        Text(
            if (showDone) "Ukryj załatwione" else "Załatwione: ${done.size} · pokaż",
            fontSize = 13.sp, color = Luxury.GoldText,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDone = !showDone }.padding(8.dp),
        )
        if (showDone) {
            LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
                LuxurySectionTitle("Załatwione")
                done.forEachIndexed { i, item ->
                    if (i > 0) LuxuryDivider()
                    ItemRow(d, item, square = false, meta = "załatwił(a): ${d.ui.name(item.checkedBy)} · ${time(item.checkedAt)}") {}
                    SubList(d, item)
                }
            }
        }
    }
    if (d.ui.canAdd(d.list)) GoldButton("+  Dodaj termin") { addFromMemory(d) }
    Text("Termin powtarzalny po odhaczeniu wraca z następną datą", fontSize = 11.sp, color = Faint, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

@Composable
private fun DeadlineRow(d: Detail, item: ListItem, today: LocalDate, late: Boolean) {
    val date = item.values.dueDate?.let { LocalDate.parse(it) } ?: today
    val color = if (late) MessageKind.WARNING.color else Luxury.Text
    val days = ChronoUnit.DAYS.between(today, date)
    val whenText = when {
        late -> "${-days} ${plural((-days).toInt(), "dzień", "dni", "dni")} po terminie"
        days == 0L -> "dziś"
        days == 1L -> "jutro"
        days < 31 -> "za $days dni"
        else -> "za ${days / 30} mies."
    }
    val meta = listOfNotNull(
        whenText,
        if (item.values.recurrence.frequency != Frequency.NONE) recurrenceWords(item) else null,
        item.values.note.takeIf { it.isNotBlank() }?.lines()?.first()?.take(40),
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.width(46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = color)
            Text(MonthShort[date.monthValue - 1], fontSize = 11.sp, letterSpacing = 1.sp, color = if (late) color else Muted)
        }
        Box(modifier = Modifier.weight(1f)) {
            ItemRow(d, item, square = false, meta = meta, metaColor = if (late) color else Muted, checkAtEnd = true) {
                if (item.values.reminders.any { it.memberId == d.ui.me.id }) Icon(AppIcons.Bell, contentDescription = "Przypomnienie", tint = Faint, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// --- groups and one item ---

/** [items] in the person's order (board 17); by person or role under small headers. */
@Composable
private fun Groups(d: Detail, items: List<ListItem>, row: @Composable (ListItem) -> Unit) {
    var first = true
    sorted(d.ui, d.list, items, d.sort).forEach { group ->
        group.title?.let {
            Text(
                "${it.uppercase()} · ${group.items.size}", fontSize = 11.sp, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = if (first) 6.dp else 14.dp, bottom = 2.dp),
            )
        }
        group.items.forEachIndexed { i, item ->
            if (i > 0) LuxuryDivider()
            row(item)
            SubList(d, item)
        }
        first = false
    }
}

/** "Mleko 3,2%": the kind is part of the name, lighter (board 11). */
internal fun nameText(values: ItemValues, checked: Boolean = false, suffix: String? = null) = buildAnnotatedString {
    withStyle(SpanStyle(textDecoration = if (checked) TextDecoration.LineThrough else null)) { append(values.name) }
    if (values.variant.isNotBlank()) withStyle(SpanStyle(color = Muted)) { append(" ${values.variant}") }
    suffix?.let { withStyle(SpanStyle(color = Muted, fontSize = 13.sp)) { append(it) } }
}

/**
 * A row: the check box, the name (opens the item), [meta] under it, [trailing] on the right, and the marks:
 * "PILNE", "Konflikt" (opens the decision) right of a short name, under a long one; "czeka na wysłanie" while not sent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemRow(
    d: Detail,
    item: ListItem,
    square: Boolean,
    meta: String? = null,
    metaColor: Color = Muted,
    checkAtEnd: Boolean = false,
    urgent: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val ui = d.ui
    val list = d.list
    val conflict = ui.data.openConflicts(item.id).isNotEmpty()
    val waiting = item.id in ui.waitingItems
    val longName = item.values.name.length + item.values.variant.length > 22
    val subs = if (item.parentId == null) ui.data.subItemsOf(item.id) else emptyList()
    val subOpen = ui.prefs.subOpen(item.id)
    val access = ui.whoSeesItem(list, item)
    // ⋮ (board 16): sub-items, details, own access — with premium "Podlisty", on items (not sub-items).
    val menu = item.parentId == null && ui.subItems && (ui.canAddTo(list, item) || ui.manageable(list))
    val check = @Composable {
        CheckBox(
            checked = item.checked, square = square, enabled = !item.pending && ui.canCheck(list, item),
            label = (if (item.checked) "Cofnij: " else if (list.type == ListType.SHOPPING) "Odhacz: " else "Zrobione: ") + item.values.name,
        ) { d.onCheck(item, !item.checked) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).alpha(if (item.checked) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!checkAtEnd) check()
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = if (subs.isEmpty()) "Szczegóły" else if (subOpen) "Zwiń" else "Rozwiń") {
                    if (subs.isEmpty()) d.onDialog(ListDialog.Item(list, item)) else ui.prefs.setSubOpen(item.id, !subOpen)
                }
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(nameText(item.values, item.checked), fontSize = 16.sp, modifier = if (longName) Modifier.fillMaxWidth() else Modifier)
                if (conflict) MessageChip(MessageKind.ERROR, "Konflikt", icon = AppIcons.Warning) { d.onDialog(ListDialog.Conflict(list, item)) }
            }
            if (urgent || meta != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (urgent) MessageChip(MessageKind.WARNING, "PILNE")
                    meta?.let { Text(it, fontSize = 12.sp, color = metaColor) }
                }
            }
            access?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(12.dp))
                    Text("inny dostęp: $it", fontSize = 11.sp, color = Muted)
                }
            }
            LinksLine(item)
            if (waiting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(AppIcons.Clock, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(12.dp))
                    Text("czeka na wysłanie", fontSize = 11.sp, color = Luxury.GoldText)
                }
            }
        }
        if (subs.isNotEmpty()) SubCounter(subs, subOpen, item.values.name) { ui.prefs.setSubOpen(item.id, !subOpen) }
        trailing()
        if (checkAtEnd) check()
        if (menu) {
            Box(
                modifier = Modifier.size(width = 32.dp, height = 44.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Opcje: ${item.values.name}", role = Role.Button) { d.onDialog(ListDialog.ItemMenu(list, item)) },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.More, contentDescription = "Opcje: ${item.values.name}", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** "allegro.pl" under the name (premium "Linki"); a tap opens the browser. */
@Composable
private fun LinksLine(item: ListItem) {
    val links = item.values.links
    if (links.isEmpty()) return
    val uri = LocalUriHandler.current
    val first = links.first()
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Otwórz link w przeglądarce", role = Role.Button) { runCatching { uri.openUri(first.url) } }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(AppIcons.Link, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(13.dp))
        Text(
            first.title.ifBlank { Lists.domain(first.url) } + if (links.size > 1) "  +${links.size - 1}" else "",
            fontSize = 12.sp, color = Luxury.GoldText, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "1/3 ⌄" of an item with a sub-list (board 16): a tap opens or closes it in place. */
@Composable
private fun SubCounter(subs: List<ListItem>, open: Boolean, name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(Luxury.GoldBase.copy(alpha = 0.12f))
            .clickable(onClickLabel = (if (open) "Zwiń: " else "Rozwiń: ") + name, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${subs.count { it.checked }}/${subs.size}", fontSize = 12.sp, color = Luxury.GoldText, fontWeight = FontWeight.Medium)
        Icon(AppIcons.ExpandMore, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(14.dp).rotate(if (open) 180f else 0f))
    }
}

/**
 * The sub-list of [parent] open in place (boards 16 and 18): the sub-items with a gold line on the left and the
 * field "+ Dodaj podzadanie". New ones need premium "Podlisty"; the ones there stay visible and can be checked.
 */
@Composable
private fun SubList(d: Detail, parent: ListItem) {
    val ui = d.ui
    val list = d.list
    if (parent.parentId != null || !ui.prefs.subOpen(parent.id)) return
    val subs = ui.data.subItemsOf(parent.id)
    val tasks = list.type == ListType.TASKS
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 8.dp)
            .drawBehind { drawLine(Luxury.GoldBase.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx()) }
            .padding(start = 8.dp),
    ) {
        subs.forEach { sub -> SubRow(d, sub, square = list.type == ListType.SHOPPING) }
        if (subs.isEmpty()) {
            Text(
                if (tasks) "Wpisz pierwsze podzadanie, np. „Kupić płyn”, „Okna w salonie”." else "Wpisz pierwszą podpozycję.",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        when {
            !ui.canAddTo(list, parent) -> Unit
            ui.subItems -> SubAddField(d, parent)
            else -> Text("Nowe podzadania — w pakiecie premium „Podlisty”", fontSize = 12.sp, color = Faint, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun SubRow(d: Detail, sub: ListItem, square: Boolean) {
    val ui = d.ui
    val list = d.list
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (sub.checked) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CheckBox(
            checked = sub.checked, square = square, enabled = !sub.pending && ui.canCheck(list, sub),
            label = (if (sub.checked) "Cofnij: " else "Odhacz: ") + sub.values.name,
        ) { d.onCheck(sub, !sub.checked) }
        Column(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Szczegóły") { d.onDialog(ListDialog.Item(list, sub)) }
                .padding(vertical = 6.dp),
        ) {
            Text(nameText(sub.values, sub.checked), fontSize = 15.sp)
            LinksLine(sub)
        }
        when {
            sub.checked -> Text("${ui.name(sub.checkedBy).ifEmpty { "?" }} · ${time(sub.checkedAt)}", fontSize = 11.sp, color = Muted)
            list.type == ListType.SHOPPING && list.look.quantity && sub.values.quantity != null ->
                QuantityChip(Lists.quantityText(sub.values, ui.data.allUnits), enabled = ui.canEdit(list, sub)) { d.onDialog(ListDialog.Quantity(list, sub)) }
            else -> ui.member(sub.values.assigneeId)?.let { PersonAvatar(it.name, it.avatar, size = 22.dp) }
        }
    }
}

/** "+ Dodaj podzadanie" / "+ Dodaj do „Stare Miasto”" under a sub-list; a new one gets the keyboard. */
@Composable
private fun SubAddField(d: Detail, parent: ListItem) {
    val ui = d.ui
    val list = d.list
    var draft by rememberSaveable(parent.id) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val hint = if (list.type == ListType.TASKS) "+ Dodaj podzadanie" else "+ Dodaj do „${parent.values.name.take(24)}”"
    fun add() {
        val text = draft.trim()
        if (text.isEmpty()) return
        val values = if (list.type == ListType.SHOPPING && list.look.quantity) {
            Lists.quickAdd(text, ui.data.allUnits) { key -> ui.data.products.firstOrNull { it.product == key } }
        } else ItemValues(name = text.replaceFirstChar { it.uppercase() })
        d.lists.addItem(list.id, values, parentId = parent.id)
        draft = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedTextField(
            value = draft, onValueChange = { draft = it },
            placeholder = { Text(hint, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
            singleLine = true, shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Outline),
            modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Dodaj do: ${parent.values.name}" },
        )
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Luxury.GoldFill)
                .clickable(onClickLabel = "Dodaj do: ${parent.values.name}", role = Role.Button) { add() },
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.Plus, contentDescription = null, tint = Luxury.Surface, modifier = Modifier.size(20.dp)) }
    }
    LaunchedEffect(ui.prefs.focusSub) {
        if (ui.prefs.focusSub == parent.id) {
            runCatching { focus.requestFocus() }
            ui.prefs.focusSub = null
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean, square: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val shape = if (square) RoundedCornerShape(7.dp) else CircleShape
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, role = Role.Checkbox, onClick = onClick).alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .then(if (checked) Modifier.background(Luxury.GoldFill) else Modifier.border(1.6.dp, Luxury.GoldBase, shape)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.Surface, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(vertical = 14.dp))
}

private fun String.cap() = replaceFirstChar { it.uppercase() }

private val MonthShort = listOf("STY", "LUT", "MAR", "KWI", "MAJ", "CZE", "LIP", "SIE", "WRZ", "PAŹ", "LIS", "GRU")

/** "dziś", "jutro", "do soboty 15.11", "wczoraj" … */
internal fun dateWords(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "dziś"
        days == 1L -> "jutro"
        days == -1L -> "wczoraj (po terminie)"
        days < 0 -> "${date.dayOfMonth}.${date.monthValue} (po terminie)"
        days < 7 -> "do ${WeekdayGenitive[date.dayOfWeek.value - 1]} ${date.dayOfMonth}.${date.monthValue}"
        else -> "do ${date.dayOfMonth}.${date.monthValue}"
    }
}

private val WeekdayGenitive = listOf("poniedziałku", "wtorku", "środy", "czwartku", "piątku", "soboty", "niedzieli")

internal fun recurrenceWords(item: ListItem): String {
    val rule = item.values.recurrence
    val every = rule.interval.coerceAtLeast(1)
    return when (rule.frequency) {
        Frequency.NONE -> ""
        Frequency.DAILY -> if (every == 1) "codziennie" else "co $every dni"
        Frequency.WEEKLY -> if (every == 1) "co tydzień" else "co $every tyg."
        Frequency.MONTHLY -> if (every == 1) "co miesiąc" else "co $every mies."
        Frequency.YEARLY -> if (every == 1) "co rok" else "co $every lata"
    }
}

/** "10:42", "wczoraj 18:10", "12.11 10:20". */
internal fun time(at: Long?): String {
    if (at == null) return ""
    val moment = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(moment.toLocalDate(), LocalDate.now())
    val hm = "%d:%02d".format(moment.hour, moment.minute)
    return when (days) {
        0L -> "dziś $hm"
        1L -> "wczoraj $hm"
        else -> "${moment.dayOfMonth}.${moment.monthValue} $hm"
    }
}

