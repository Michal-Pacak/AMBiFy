package pl.ambif.ambify.features.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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
    /** Board 16: "Otwórz na osobnym ekranie ›" of a sub-list (null: back to the list). */
    val onOpenSub: (String?) -> Unit = {},
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
    /** Board 16: the item whose sub-list is open on its own screen, or null. */
    subOf: String? = null,
    onOpenSub: (String?) -> Unit = {},
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
    val detail = Detail(ui, list, lists, onDialog, onCheck, items, onInfo, onOpenSub)
    val parent = subOf?.let { id -> all.firstOrNull { it.id == id && it.parentId == null } }
    if (subOf != null) {
        if (parent == null) LaunchedEffect(subOf) { onOpenSub(null) } else SubScreen(detail, parent)
        return
    }
    val level = ui.level(list)
    val marked = ui.prefs.marked
    // Board 27: marking belongs to this list only; "Wstecz" ends it first.
    LaunchedEffect(list.id) { marked.clear() }
    BackHandler(enabled = marked.isNotEmpty()) { marked.clear() }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (marked.isNotEmpty()) 120.dp else 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (marked.isNotEmpty()) MarkingHeader(marked.size) { marked.clear() } else
        // The buttons in one row, the list's name and who sees it under them on the whole width (Michał, 22.11).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Wróć", tint = Luxury.GoldText) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDialog(ListDialog.SortItems(list)) }) { Icon(AppIcons.Sort, contentDescription = "Sortuj", tint = Muted) }
                CloseButton { onDialog(ListDialog.Close(list)) }
                if (ui.manageable(list)) {
                    IconButton(onClick = { onDialog(ListDialog.Menu(list)) }) { Icon(AppIcons.More, contentDescription = "Opcje listy", tint = Muted) }
                }
                pl.ambif.ambify.ui.help.HelpButton(pl.ambif.ambify.ui.help.Help.LIST)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(list.name, fontSize = 22.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${kindLabel(list)} · ${ui.whoSees(list)}" + if (list.archived) " · w archiwum" else "", fontSize = 13.sp, color = Muted, maxLines = 2)
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
    if (marked.isNotEmpty()) MarkingBar(detail, Modifier.align(Alignment.BottomCenter))
    }
}

/** Board 27: instead of the list's title while items are marked. */
@Composable
private fun MarkingHeader(count: Int, onEnd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(16.dp)).background(Luxury.GoldBase.copy(alpha = 0.12f)).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onEnd) { Icon(AppIcons.Close, contentDescription = "Zakończ zaznaczanie", tint = Luxury.GoldText) }
        Text("Zaznaczone: $count", fontSize = 18.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("stuknij, żeby dodać\nlub odjąć", fontSize = 11.sp, color = Muted, textAlign = TextAlign.End)
    }
}

/** Board 27: what to do with the marked items — into a group, check them, delete them. */
@Composable
private fun MarkingBar(d: Detail, modifier: Modifier) {
    val ui = d.ui
    val list = d.list
    val items = ui.prefs.marked.mapNotNull { ui.data.item(it) }
    Row(
        modifier = modifier.fillMaxWidth().background(Luxury.Surface).drawBehind {
            drawLine(Luxury.GoldBase.copy(alpha = 0.35f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (list.type != ListType.DEADLINES) {
            BarAction("Do grupy", AppIcons.GroupAdd, gold = true, modifier = Modifier.weight(1f)) {
                when {
                    !ui.subItems -> d.onInfo(MessageKind.INFO, "Grupy są w pakiecie premium", "Zaznaczone możesz teraz odhaczyć albo usunąć.")
                    items.any { !ui.canEdit(list, it) } -> d.onInfo(MessageKind.INFO, "Nie wszystkie są Twoje", "Cudze pozycje przenosi ktoś z dostępem „edytuje i usuwa”. Odznacz je.")
                    else -> d.onDialog(ListDialog.ToGroup(list, items.map { it.id }))
                }
            }
        }
        BarAction(if (list.type == ListType.SHOPPING) "Odhacz" else "Zrobione", AppIcons.Check, modifier = Modifier.weight(1f)) {
            val open = items.filter { !it.checked && ui.canCheck(list, it) }
            d.lists.checkAll(open)
            ui.prefs.marked.clear()
            d.onInfo(MessageKind.SUCCESS, "Odhaczono", countWord(open.size, list) + ".")
        }
        BarAction("Usuń", AppIcons.Trash, danger = true, modifier = Modifier.weight(1f)) {
            if (items.any { !ui.canEdit(list, it) }) {
                d.onInfo(MessageKind.INFO, "Nie wszystkie są Twoje", "Cudze pozycje usuwa ktoś z dostępem „edytuje i usuwa”. Odznacz je.")
            } else d.onDialog(ListDialog.DeleteMany(list, items.map { it.id }))
        }
    }
}

@Composable
private fun BarAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, gold: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val color = when {
        gold -> Luxury.OnGold
        danger -> MessageKind.ERROR.color
        else -> Luxury.Text
    }
    Column(
        modifier = modifier.heightIn(min = 56.dp).clip(shape)
            .then(if (gold) Modifier.background(Luxury.GoldFill) else Modifier.border(1.dp, if (danger) MessageKind.ERROR.color.copy(alpha = 0.6f) else Outline, shape))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = if (gold) Luxury.OnGold else if (danger) color else Luxury.GoldText, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
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
        Text("Zamknij", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Luxury.GoldText, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont)
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
    if (ui.canAdd(list) && ui.prefs.marked.isEmpty()) {
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
            ) { Icon(AppIcons.Plus, contentDescription = if (draft.isBlank()) "Dodaj z pamięci" else "Dodaj pozycję", tint = Luxury.OnGold) }
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
    ) { Text(text.ifEmpty { "+ ilość" }, fontSize = 12.sp, fontFamily = Luxury.HeadingFont, color = if (text.isEmpty()) Faint else Luxury.GoldText, maxLines = 1) }
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
                "${it.uppercase()} · ${group.items.size}", fontSize = 11.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, letterSpacing = 1.2.sp, color = Muted, fontWeight = FontWeight.Medium,
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
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    val marking = ui.prefs.marked.isNotEmpty()
    // Board 27: a group itself is not marked (it does not go into another one).
    val markable = item.parentId == null && subs.isEmpty() && !item.pending
    val isMarked = item.id in ui.prefs.marked
    val toggleMark = { if (isMarked) ui.prefs.marked.remove(item.id) else ui.prefs.marked.add(item.id) }
    val menu = !marking && item.parentId == null && ui.subItems && (ui.canAddTo(list, item) || ui.manageable(list))
    val check = @Composable {
        if (marking) MarkCircle(isMarked, markable, item.values.name) { toggleMark() } else
        CheckBox(
            checked = item.checked, square = square, enabled = !item.pending && ui.canCheck(list, item),
            label = (if (item.checked) "Cofnij: " else if (list.type == ListType.SHOPPING) "Odhacz: " else "Zrobione: ") + item.values.name,
        ) { d.onCheck(item, !item.checked) }
    }
    val counter = @Composable {
        // An open sub-list shows its counter also while empty ("0/0 ⌄", board 16), so it can be closed again.
        if (subs.isNotEmpty() || (subOpen && item.parentId == null)) SubCounter(subs, subOpen, item.values.name) { ui.prefs.setSubOpen(item.id, !subOpen) }
    }
    val menuButton = @Composable {
        if (menu) {
            Box(
                modifier = Modifier.size(width = 32.dp, height = 44.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Opcje: ${item.values.name}", role = Role.Button) { d.onDialog(ListDialog.ItemMenu(list, item)) },
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.More, contentDescription = "Opcje: ${item.values.name}", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
    }
    val marks = @Composable {
        AttachmentsLine(ui, d.lists, item, onPhotos = { d.onDialog(ListDialog.Photos(list, item)) },
            onFile = { d.onDialog(ListDialog.OpenFile(list, item, it)) }, onFiles = { d.onDialog(ListDialog.Item(list, item)) })
    }
    @Composable
    fun row(stacked: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .then(if (isMarked) Modifier.clip(RoundedCornerShape(12.dp)).background(Luxury.GoldBase.copy(alpha = 0.14f)) else Modifier)
                .alpha(if (marking && !markable) 0.45f else if (item.checked) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!checkAtEnd || marking) check()
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClickLabel = when {
                            marking -> if (isMarked) "Odznacz" else "Zaznacz"
                            subs.isEmpty() -> "Szczegóły"
                            subOpen -> "Zwiń"
                            else -> "Rozwiń"
                        },
                        // Board 27: holding an item starts marking (several into a group, checked or deleted at once).
                        onLongClickLabel = if (!marking && markable) "Zaznacz kilka" else null,
                        onLongClick = if (!marking && markable) ({ ui.prefs.marked.add(item.id) }) else null,
                    ) {
                        when {
                            marking -> if (markable) toggleMark()
                            subs.isEmpty() -> d.onDialog(ListDialog.Item(list, item))
                            else -> ui.prefs.setSubOpen(item.id, !subOpen)
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(nameText(item.values, item.checked), fontSize = 16.sp, modifier = if (longName || stacked) Modifier.fillMaxWidth() else Modifier)
                    if (conflict) MessageChip(MessageKind.ERROR, "Konflikt", icon = AppIcons.Warning) { d.onDialog(ListDialog.Conflict(list, item)) }
                }
                if (marking && subs.isNotEmpty()) Text("grupa — nie wkłada się jej do innej", fontSize = 12.sp, color = Muted)
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
                if (stacked) {
                    // Little room (a big font, a narrow screen): amount, person and marks under the name (Michał, 22.11).
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        counter()
                        trailing()
                        marks()
                    }
                } else {
                    marks()
                }
                if (waiting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.Clock, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(12.dp))
                        Text("czeka na wysłanie", fontSize = 11.sp, color = Luxury.GoldText)
                    }
                }
            }
            if (!stacked) {
                counter()
                trailing()
            }
            if (checkAtEnd && !marking) check()
            menuButton()
        }
    }
    // The name gets at least about 7 letters of width; else the things on the right go under it.
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val side = subcompose("side") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                check(); counter(); trailing(); menuButton()
            }
        }.map { it.measure(Constraints()) }.maxOfOrNull { it.width } ?: 0
        val stacked = constraints.maxWidth - side - 10.dp.roundToPx() < MIN_NAME.roundToPx()
        val placeables = subcompose(stacked) { row(stacked) }.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, placeables.maxOfOrNull { it.height } ?: 0) { placeables.forEach { it.place(0, 0) } }
    }
}

/** The least width of an item's name before the amount and the person go under it (scales with the font). */
private val MIN_NAME = 100.sp

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

/** Board 27: the round mark of a marked item (instead of its check box while marking). */
@Composable
private fun MarkCircle(on: Boolean, enabled: Boolean, name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = if (on) "Odznacz: $name" else "Zaznacz: $name", role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape)
                .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.6.dp, Muted, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.OnGold, modifier = Modifier.size(14.dp)) }
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
    val all = ui.data.subItemsOf(parent.id)
    // Board 17: the sub-list's own order (or the list's); done ones after the open ones, as on the separate screen.
    val subs = sorted(ui, list, all.filter { !it.checked }, ui.prefs.subSort(list, parent)).flatMap { it.items } +
        all.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 }
    val tasks = list.type == ListType.TASKS
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 8.dp)
            .drawBehind { drawLine(Luxury.GoldBase.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx()) }
            .padding(start = 8.dp),
    ) {
        subs.forEach { sub -> SubRow(d, sub, square = list.type == ListType.SHOPPING) }
        if (subs.isEmpty()) {
            Text(
                if (tasks) "Wpisz pierwsze zadanie tej grupy, np. „Kupić płyn”, „Okna w salonie”." else "Wpisz pierwszą rzecz tej grupy.",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        when {
            !ui.canAddTo(list, parent) -> Unit
            ui.subItems -> SubAddField(d, parent)
            else -> Text("Dodawanie do grup — w pakiecie premium „Grupy”", fontSize = 12.sp, color = Faint, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (subs.isNotEmpty()) {
            Text(
                "Otwórz na osobnym ekranie ›", fontSize = 12.sp, color = Luxury.GoldText,
                modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Otwórz grupę „${parent.values.name}” na osobnym ekranie", role = Role.Button) { d.onOpenSub(parent.id) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
            )
        }
    }
}

/**
 * Board 16, "Otwórz na osobnym ekranie": the same sub-list with the list's name above (back = to the list), a whole
 * add field and the order of the list. Handy for long sub-lists; the last sub-item done asks about its item, as on the list.
 */
@Composable
private fun SubScreen(d: Detail, parent: ListItem) {
    val ui = d.ui
    val list = d.list
    val subs = ui.data.subItemsOf(parent.id)
    val tasks = list.type == ListType.TASKS
    val order = sorted(ui, list, subs.filter { !it.checked }, ui.prefs.subSort(list, parent))
    val done = subs.filter { it.checked }.sortedByDescending { it.checkedAt ?: 0 }
    val other = ui.whoSeesItem(list, parent)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { d.onOpenSub(null) }) { Icon(AppIcons.Back, contentDescription = "Wróć do listy ${list.name}", tint = Luxury.GoldText) }
            Column(modifier = Modifier.weight(1f)) {
                Text("${list.name} ›", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(parent.values.name, fontSize = 22.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "grupa · ${done.size}/${subs.size}" + (other?.let { " · inny dostęp: $it" } ?: ""),
                    fontSize = 12.sp, color = Muted, maxLines = 2,
                )
            }
            IconButton(onClick = { d.onDialog(ListDialog.SortSub(list, parent)) }) {
                Icon(AppIcons.Sort, contentDescription = "Sortuj grupę", tint = Muted)
            }
        }
        SyncBar(ui, onRetry = {})
        when {
            !ui.canAddTo(list, parent) -> MessageBar(MessageKind.INFO, "Tylko do odhaczania", "Nowe dodaje ktoś z dostępem „dodaje”. Dostęp ustala twórca listy.", icon = AppIcons.Lock)
            ui.subItems -> SubAddField(d, parent)
            else -> MessageBar(MessageKind.INFO, "Pakiet premium „Grupy”", "To, co jest w grupie, zostaje i można to odhaczać. Nowe dodasz z pakietem premium.", icon = AppIcons.Lock)
        }
        LuxuryCard(contentPadding = CardPadding, spacing = 0.dp) {
            LuxurySectionTitle("W grupie · ${done.size}/${subs.size}")
            if (subs.isEmpty()) EmptyLine(if (tasks) "Wpisz pierwsze zadanie tej grupy, np. „Kupić płyn”." else "Wpisz pierwszą rzecz tej grupy.")
            order.forEach { group ->
                group.title?.let { Text(it, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
                group.items.forEach { sub -> SubRow(d, sub, square = list.type == ListType.SHOPPING) }
            }
            done.forEach { sub -> SubRow(d, sub, square = list.type == ListType.SHOPPING) }
        }
        Text(
            "Gdy odhaczysz ${if (tasks) "ostatnie zadanie" else "ostatnią rzecz"} w grupie, zapytamy, czy odhaczyć też „${parent.values.name}” na liście.",
            fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
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
            AttachmentsLine(ui, d.lists, sub, onPhotos = { d.onDialog(ListDialog.Photos(list, sub)) },
                onFile = { d.onDialog(ListDialog.OpenFile(list, sub, it)) }, onFiles = { d.onDialog(ListDialog.Item(list, sub)) })
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
    val hint = "+ Dodaj do grupy"
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
        ) { Icon(AppIcons.Plus, contentDescription = null, tint = Luxury.OnGold, modifier = Modifier.size(20.dp)) }
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
        ) { if (checked) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.OnGold, modifier = Modifier.size(15.dp)) }
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

