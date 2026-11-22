package pl.ambif.ambify.features.lists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.DateDialog
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.features.timetable.PersonChip
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListMemory
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.ui.components.FieldError
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageChip
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryPopup

/** Board 22: the same thing already on the list — add up, replace its amount, or add it apart. */
private enum class Twice { SUM, REPLACE, APART }

/** One thing picked from the memory, with its parameters as they will be added (board 22). */
private class Pick(val memory: ListMemory, values: ItemValues, twice: Twice) {
    var values by mutableStateOf(values)
    var twice by mutableStateOf(twice)
}

/** Most things shown at once in "Z pamięci"; searching finds the others. */
private const val MAX_SHOWN = 80

/**
 * Boards 21–23: "Z pamięci" — the things the community added before to this kind of lists. Step 1 picks (search,
 * this list / all, sorting; hold = "Usuń z pamięci"), step 2 sets each one's amount, kind, person or date and adds
 * them all at once (also offline). A thing already on the list or done is never added twice without asking.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryDialog(
    ui: ListsUi,
    list: ListInfo,
    lists: ListsController,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onSaved: (String, String) -> Unit,
) {
    val scope = Lists.scopeOf(list)
    val memory = ui.data.memoryOf(scope)
    val onList = remember(ui.data.items, list.id) { ui.data.items.filter { it.listId == list.id }.map { Lists.memoryKey(it.values) }.toSet() }
    val items = ui.data.itemsOf(list.id)
    var query by remember { mutableStateOf("") }
    var thisList by remember { mutableStateOf(memory.any { it.key in onList }) }
    var sort by remember { mutableStateOf(MemorySort.OFTEN) }
    val chosen = remember { mutableStateListOf<String>() }
    var step2 by remember { mutableStateOf(false) }
    val picks = remember { mutableStateListOf<Pick>() }
    var forget by remember { mutableStateOf<ListMemory?>(null) }
    var tried by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Pick?>(null) }

    fun openOf(values: ItemValues) = items.firstOrNull { !it.checked && Lists.sameThing(it.values, values) }
    fun doneOf(values: ItemValues) = items.firstOrNull { it.checked && Lists.sameThing(it.values, values) }
    val what = when (list.type) {
        ListType.SHOPPING -> "pozycje"
        ListType.TASKS -> "zadania"
        ListType.DEADLINES -> "terminy"
    }

    val shown = memoryShown(memory, query, if (thisList) onList else null, sort)
    val count = picks.size
    val missingDate = list.type == ListType.DEADLINES && picks.any { it.values.dueDate == null && !it.values.urgent }
    val title = @Composable { text: String ->
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(list.name, fontSize = 12.sp, color = Muted)
        }
    }

    // Title, search and filters stay at the top, the buttons at the bottom; only the things in the middle scroll.
    LuxuryPopup(
        onDismiss = onDismiss,
        header = {
            if (!step2) {
                title(
                    when (list.type) {
                        ListType.SHOPPING -> "Z pamięci"
                        ListType.TASKS -> "Dodaj zadanie"
                        ListType.DEADLINES -> "Dodaj termin"
                    },
                )
                if (list.type != ListType.SHOPPING) {
                    FramedButton(if (list.type == ListType.TASKS) "+  Nowe zadanie" else "+  Nowy termin", modifier = Modifier.fillMaxWidth()) {
                        onDismiss()
                        onNew()
                    }
                    if (memory.isNotEmpty()) Text("albo wybierz z wcześniej dodanych:", fontSize = 12.sp, color = Muted)
                }
                if (memory.isNotEmpty()) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                        placeholder = { Text("Szukaj…") }, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Z tej listy", thisList) { thisList = true }
                        Chip("Wszystkie", !thisList) { thisList = false }
                        MemorySort.entries.forEach { option -> Chip(option.label, sort == option) { sort = option } }
                    }
                }
            } else {
                title("Dodajesz $count ${plural(count, "pozycję", "pozycje", "pozycji")}")
            }
        },
        footer = {
            if (!step2) {
                if (memory.isEmpty()) {
                    FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
                    return@LuxuryPopup
                }
                Text("Przytrzymaj, by usunąć z pamięci.", fontSize = 11.sp, color = Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                    Box(modifier = Modifier.weight(1f)) {
                        GoldButton(if (chosen.isEmpty()) "Dodaj" else "Dodaj (${chosen.size})", enabled = chosen.isNotEmpty()) {
                            val byKey = memory.associateBy { it.key }
                            val kept = picks.associateBy { it.memory.key }
                            picks.clear()
                            chosen.mapNotNull { byKey[it] }.forEach { entry ->
                                picks += kept[entry.key] ?: newPick(ui, list, entry, openOf(entry.values))
                            }
                            tried = false
                            step2 = true
                        }
                    }
                }
            } else {
                if (tried && missingDate) FieldError("Każdy termin potrzebuje daty albo „PILNE”.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FramedButton("Wstecz", gold = false, modifier = Modifier.weight(1f)) { step2 = false }
                    Box(modifier = Modifier.weight(1.4f)) {
                        GoldButton("Dodaj na listę ($count)", enabled = count > 0) {
                            tried = true
                            if (missingDate) return@GoldButton
                            picks.forEach { pick -> addPick(ui, list, lists, pick, openOf(pick.values), doneOf(pick.values)) }
                            onDismiss()
                            onSaved(
                                "Dodano $count ${plural(count, "pozycję", "pozycje", "pozycji")}",
                                picks.joinToString(", ") { nameOf(it.values) }.take(120),
                            )
                        }
                    }
                }
            }
        },
    ) {
        if (!step2) {
            if (memory.isEmpty()) {
                MessageBar(MessageKind.INFO, "Pamięć jest jeszcze pusta", "Zapełni się sama, gdy będziecie dodawać $what — wspólna dla całej społeczności.")
                return@LuxuryPopup
            }
            Column {
                if (shown.isEmpty()) {
                    Text(
                        if (query.isNotBlank()) "Nic nie pasuje do „${query.trim()}”." else "Na tej liście nie było jeszcze nic z pamięci — zobacz „Wszystkie”.",
                        fontSize = 13.sp, color = Muted, modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                shown.take(MAX_SHOWN).forEach { entry ->
                    val on = entry.key in chosen
                    MemoryRow(ui, list, entry, on, status(list, openOf(entry.values), doneOf(entry.values)),
                        onToggle = { if (on) chosen.remove(entry.key) else chosen.add(entry.key) },
                        onHold = { forget = entry },
                    )
                }
                if (shown.size > MAX_SHOWN) Text("…i ${shown.size - MAX_SHOWN} więcej — wpisz, czego szukasz.", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            picks.toList().forEach { pick ->
                PickCard(ui, list, pick, openOf(pick.values), doneOf(pick.values), tried,
                    onRemove = { picks.remove(pick); chosen.remove(pick.memory.key); if (picks.isEmpty()) step2 = false },
                    onDate = { picking = pick },
                )
            }
        }
    }

    forget?.let { entry ->
        LuxuryPopup(onDismiss = { forget = null }) {
            Text("Usunąć „${nameOf(entry.values)}” z pamięci?", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Zniknie z podpowiedzi u wszystkich w społeczności. Pozycje na listach zostają; dodany ponownie wróci do pamięci.", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            DangerButton("Usuń z pamięci", {
                lists.forget(list.id, entry.values)
                chosen.remove(entry.key)
                forget = null
            })
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth()) { forget = null }
        }
    }
    picking?.let { pick ->
        DateDialog(pick.values.dueDate?.let { LocalDate.parse(it) } ?: LocalDate.now(), onDismiss = { picking = null }) {
            pick.values = pick.values.copy(dueDate = it.toString(), urgent = false)
            picking = null
        }
    }
}

/** "na liście" or "kupione" / "zrobione" (picking it puts it back), else nothing. */
private fun status(list: ListInfo, open: ListItem?, done: ListItem?): String? = when {
    open != null -> "na liście"
    done != null -> if (list.type == ListType.SHOPPING) list.look.doneLabel else "zrobione"
    else -> null
}

/** The parameters under a remembered thing: "1 l · ostatnio Ula", "Adam · co tydzień". */
private fun memoryMeta(ui: ListsUi, list: ListInfo, entry: ListMemory): String {
    val v = entry.values
    val parts = mutableListOf<String>()
    when (list.type) {
        ListType.SHOPPING -> if (list.look.quantity) Lists.quantityText(v, ui.data.allUnits).takeIf { it.isNotEmpty() }?.let { parts += it }
        ListType.TASKS -> parts += v.assigneeId?.let { ui.name(it) } ?: "bez osoby"
        ListType.DEADLINES -> Unit
    }
    if (list.type != ListType.SHOPPING && v.recurrence.frequency != Frequency.NONE) parts += recurrenceWords(ListItem("", "", v))
    if (list.type == ListType.SHOPPING) entry.lastBy?.let { parts += "ostatnio ${ui.name(it)}" }
    return parts.joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryRow(ui: ListsUi, list: ListInfo, entry: ListMemory, on: Boolean, status: String?, onToggle: () -> Unit, onHold: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onHold, onClickLabel = if (on) "Odznacz" else "Wybierz", onLongClickLabel = "Usuń z pamięci", role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val shape = RoundedCornerShape(if (list.type == ListType.SHOPPING) 7.dp else 11.dp)
        Box(
            modifier = Modifier.size(22.dp).clip(shape)
                .then(if (on) Modifier.background(Luxury.GoldFill) else Modifier.border(1.6.dp, Luxury.GoldBase, shape)),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(AppIcons.Check, contentDescription = null, tint = Luxury.Surface, modifier = Modifier.size(15.dp)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nameText(entry.values), fontSize = 15.sp)
            memoryMeta(ui, list, entry).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 12.sp, color = Muted) }
        }
        status?.let { Tag(it, done = it != "na liście") }
    }
}

@Composable
private fun Tag(text: String, done: Boolean) {
    val color = if (done) MessageKind.SUCCESS.color else Muted
    Text(
        text, fontSize = 11.sp, color = color, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(9.dp)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(9.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** The values a picked thing starts with; on the list already: add up when the units allow it, else replace. */
private fun newPick(ui: ListsUi, list: ListInfo, entry: ListMemory, open: ListItem?): Pick {
    var values = entry.values.copy(reminders = emptyList())
    if (list.type == ListType.SHOPPING && !list.look.quantity) values = values.copy(quantity = null, unit = null, packSize = null)
    if (list.type == ListType.TASKS && values.assigneeId != null && values.assigneeId != ui.me.id && ui.tasksForOthers == Mode.DENIED) values = values.copy(assigneeId = null)
    if (list.type == ListType.DEADLINES && values.recurrence.frequency == Frequency.NONE) values = values.copy(recurrence = Recurrence())
    val twice = when {
        open == null -> Twice.APART
        list.type == ListType.SHOPPING && list.look.quantity && Lists.sum(open.values, values) != null -> Twice.SUM
        else -> Twice.REPLACE
    }
    return Pick(entry, values, twice)
}

/** Adds one picked thing as board 22 says; each change has its own id, so it also works offline. */
private fun addPick(ui: ListsUi, list: ListInfo, lists: ListsController, pick: Pick, open: ListItem?, done: ListItem?) {
    val values = pick.values
    when {
        open != null && pick.twice == Twice.SUM ->
            Lists.sum(open.values, values)?.let { lists.edit(open, it.copy(name = open.values.name, variant = open.values.variant)) }
                ?: lists.edit(open, restoredValues(open.values, values))
        open != null && pick.twice == Twice.REPLACE -> lists.edit(open, restoredValues(open.values, values))
        open == null && done != null && ui.canCheck(list) -> lists.restore(done, restoredValues(done.values, values))
        else -> lists.addItem(list.id, values)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickCard(ui: ListsUi, list: ListInfo, pick: Pick, open: ListItem?, done: ListItem?, tried: Boolean, onRemove: () -> Unit, onDate: () -> Unit) {
    val values = pick.values
    val units = ui.data.allUnits
    val quantity = list.type == ListType.SHOPPING && list.look.quantity
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Outline, RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1B1F)).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(nameText(values), fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (open == null && done != null) Tag(if (list.type == ListType.SHOPPING) "wróci z: ${list.look.doneLabel}" else "wróci ze zrobionych", done = true)
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClickLabel = "Usuń z wyboru: ${values.name}", role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Close, contentDescription = "Usuń z wyboru", tint = Muted, modifier = Modifier.size(18.dp)) }
        }
        if (quantity) {
            val unit = units.firstOrNull { it.name == values.unit }
            val step = unit?.step ?: 1.0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepCircle(AppIcons.Minus, "Mniej") {
                    val q = values.quantity ?: return@StepCircle
                    val next = Math.round((q - step) * 10) / 10.0
                    pick.values = if (next <= 0) values.copy(quantity = null, unit = null, packSize = null) else values.copy(quantity = next)
                }
                Text(
                    Lists.quantityText(values, units).ifEmpty { "bez ilości" }, fontSize = 18.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp),
                )
                StepCircle(AppIcons.Plus, "Więcej") {
                    val q = values.quantity
                    pick.values = if (q == null) values.copy(quantity = if (step >= 1) step else 1.0, unit = values.unit ?: "szt.") else values.copy(quantity = Math.round((q + step) * 10) / 10.0)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOfNotNull(values.unit) + listOf("szt.", "kg", "dag", "l", "opak.")).distinct().forEach { name ->
                    Chip(name, values.unit == name) {
                        val q = values.quantity ?: 1.0
                        pick.values = values.copy(unit = name, quantity = if (name == "dag" && q < 10) 30.0 else q, packSize = null)
                    }
                }
            }
        }
        if (list.type == ListType.SHOPPING) {
            val variants = (ui.data.variantsOf(values.name) + values.variant).filter { it.isNotBlank() }.distinct()
            if (variants.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    variants.take(6).forEach { v -> Chip(v, values.variant == v) { pick.values = values.copy(variant = if (values.variant == v) "" else v) } }
                }
            }
        }
        if (list.type == ListType.TASKS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Bez osoby", values.assigneeId == null) { pick.values = values.copy(assigneeId = null) }
                ui.members.filter { it.id == ui.me.id || ui.tasksForOthers != Mode.DENIED }.forEach { member ->
                    PersonChip(if (member.id == ui.me.id) "Ty" else member.name, member.avatar, values.assigneeId == member.id) {
                        pick.values = values.copy(assigneeId = member.id)
                    }
                }
            }
        }
        if (list.type != ListType.SHOPPING) {
            val error = tried && list.type == ListType.DEADLINES && values.dueDate == null && !values.urgent
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (list.type == ListType.TASKS) Chip("Bez terminu", values.dueDate == null && !values.urgent) { pick.values = values.copy(dueDate = null, urgent = false) }
                Chip(values.dueDate?.let { dateWords(LocalDate.parse(it)) } ?: "Wybierz datę…", values.dueDate != null, onClick = onDate)
                Chip("PILNE", values.urgent) { pick.values = values.copy(urgent = !values.urgent, dueDate = null) }
            }
            if (values.recurrence.frequency != Frequency.NONE && values.dueDate == null && !values.urgent) {
                Text("${recurrenceWords(ListItem("", "", values)).replaceFirstChar { it.uppercase() }} — wybierz pierwszą datę.", fontSize = 12.sp, color = Muted)
            }
            if (error) FieldError("Wybierz datę albo PILNE.")
        }
        open?.let { existing ->
            val sum = if (quantity) Lists.sum(existing.values, values) else null
            val now = if (quantity) Lists.quantityText(existing.values, units) else ""
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MessageChip(MessageKind.INFO, "Już na liście" + if (now.isNotEmpty()) ": $now" else "")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sum != null) Chip("Razem ${Lists.quantityText(sum, units)}", pick.twice == Twice.SUM) { pick.twice = Twice.SUM }
                Chip(if (quantity) "Zamień" else "Zostaw jedną", pick.twice == Twice.REPLACE) { pick.twice = Twice.REPLACE }
                Chip("Osobno", pick.twice == Twice.APART) { pick.twice = Twice.APART }
            }
        }
    }
}
