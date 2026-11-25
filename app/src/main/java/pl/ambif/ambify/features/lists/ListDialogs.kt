package pl.ambif.ambify.features.lists

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalUriHandler
import pl.ambif.ambify.shared.ItemLink
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import pl.ambif.ambify.features.account.SwitchRow
import pl.ambif.ambify.features.timetable.Chip
import pl.ambif.ambify.features.timetable.DateDialog
import pl.ambif.ambify.features.timetable.FramedButton
import pl.ambif.ambify.features.timetable.GoldButton
import pl.ambif.ambify.features.timetable.PersonChip
import pl.ambif.ambify.features.timetable.RecurrenceDialog
import pl.ambif.ambify.features.timetable.TimeDialog
import pl.ambif.ambify.features.timetable.describe
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import pl.ambif.ambify.shared.AccessLevel
import pl.ambif.ambify.shared.ConflictDecision
import pl.ambif.ambify.shared.ConflictVersion
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListAccess
import pl.ambif.ambify.shared.ListConflict
import pl.ambif.ambify.shared.ListFinish
import pl.ambif.ambify.shared.ListIcon
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListPreset
import pl.ambif.ambify.shared.ListStyle
import pl.ambif.ambify.shared.ListReminder
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.Mode
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.ui.components.FieldError
import pl.ambif.ambify.ui.components.MessageBar
import pl.ambif.ambify.ui.components.MessageKind
import pl.ambif.ambify.ui.components.AvatarZoom
import pl.ambif.ambify.ui.components.PersonAvatar
import pl.ambif.ambify.ui.icons.AppIcons
import pl.ambif.ambify.ui.theme.Luxury
import pl.ambif.ambify.ui.theme.LuxuryDivider
import pl.ambif.ambify.ui.theme.LuxuryOpenMark
import pl.ambif.ambify.ui.theme.LuxuryPopup
import pl.ambif.ambify.ui.theme.PopupButtons
import pl.ambif.ambify.ui.theme.LuxurySectionTitle

@Composable
internal fun ListDialogs(
    dialog: ListDialog?,
    ui: ListsUi,
    lists: ListsController,
    onDialog: (ListDialog?) -> Unit,
    onCreated: (String) -> Unit,
    onClosed: () -> Unit,
    onResolve: (String, ConflictDecision) -> Unit,
    onSaved: (String, String) -> Unit,
    /** A result with "Cofnij" (board 27). */
    onUndoable: (String, String, () -> Unit) -> Unit = { title, text, _ -> onSaved(title, text) },
) {
    val close = { onDialog(null) }
    when (dialog) {
        null -> Unit
        ListDialog.NewList -> ListForm(ui, null, close) { draft -> onDialog(ListDialog.Access(draft, isNew = true)) }
        is ListDialog.EditList -> ListForm(ui, dialog.list, close) { changed ->
            lists.updateList(changed)
            close()
            onSaved("Zapisano", "Lista „${changed.name}”.")
        }
        is ListDialog.Access -> AccessEditor(ui, dialog.list, dialog.isNew, onDismiss = close) { access ->
            if (dialog.isNew) {
                val id = lists.createList(dialog.list.copy(access = access))
                close()
                onCreated(id)
            } else {
                lists.updateList(dialog.list.copy(access = access, everyone = access.isEmpty() && dialog.list.everyone))
                close()
                onSaved("Zapisano dostęp", "Lista „${dialog.list.name}”.")
            }
        }
        is ListDialog.Done -> DoneDialog(ui, dialog.list, lists, close,
            onArchive = {
                lists.updateList(dialog.list.copy(archived = true))
                close()
                onClosed()
                onSaved("Przeniesiono do archiwum", "Lista „${dialog.list.name}”.")
            },
            onDelete = { onDialog(ListDialog.DeleteList(dialog.list)) },
        )
        is ListDialog.Duplicate -> DuplicateDialog(ui, dialog, lists, close)
        is ListDialog.Merge -> MergeDialog(ui, dialog, lists, close)
        ListDialog.SortLists -> SortDialog(
            title = "Sortuj listy", where = "Poziom: wszystkie listy", whereText = "Sortujesz listy na tym ekranie. Zawartość list się nie zmienia.",
            options = ListsSort.entries.map { Triple(it.label, it.description, it == ui.prefs.listsSort()) },
            onPick = { i -> ui.prefs.setListsSort(ListsSort.entries[i]); close() }, onDismiss = close,
        )
        is ListDialog.SortItems -> {
            val what = when (dialog.list.type) {
                ListType.SHOPPING -> "pozycje"
                ListType.TASKS -> "zadania"
                ListType.DEADLINES -> "terminy"
            }
            val current = ui.prefs.itemSort(dialog.list)
            val hasSubs = ui.data.items.any { it.listId == dialog.list.id && it.parentId != null && !it.cleared }
            val same = ui.prefs.subsSameSort(dialog.list.id)
            SortDialog(
                title = "Sortuj $what", where = "Poziom: lista „${dialog.list.name}”",
                whereText = "Sortujesz $what tej listy, w każdej sekcji osobno." + if (hasSubs) " Grupy sortujesz po wejściu do nich." else "",
                options = ItemSort.entries.map { Triple(it.label, it.description, it == current) },
                onPick = { i -> ui.prefs.setItemSort(dialog.list, ItemSort.entries[i]); close() }, onDismiss = close,
                inherit = if (!hasSubs) null else InheritToggle(
                    on = same,
                    text = if (same) "Grupy bez własnego wyboru też: ${current.label.lowercase()}." else "Grupy bez własnego wyboru: kolejność dodania.",
                    onToggle = { ui.prefs.setSubsSameSort(dialog.list.id, !same) },
                ),
            )
        }
        is ListDialog.SortSub -> {
            val tasks = dialog.list.type == ListType.TASKS
            val own = ui.prefs.ownSubSort(dialog.parent.id)
            val listSort = ui.prefs.itemSort(dialog.list)
            val same = ui.prefs.subsSameSort(dialog.list.id)
            val name = dialog.parent.values.name.let { if (it.length > 24) it.take(22).trimEnd() + "…" else it }
            SortDialog(
                title = "Sortuj grupę", where = "Poziom: grupa „$name”",
                whereText = if (tasks) "Sortujesz tylko zadania w tej grupie." else "Sortujesz tylko to, co jest w tej grupie.",
                options = listOf(Triple(
                    "Jak lista „${dialog.list.name}”",
                    if (same) "domyślnie · teraz: ${listSort.label.lowercase()}" else "lista ma wyłączone dziedziczenie — kolejność dodania",
                    own == null,
                )) + ItemSort.entries.map { Triple(it.label, it.description, it == own) },
                onPick = { i -> ui.prefs.setOwnSubSort(dialog.parent.id, if (i == 0) null else ItemSort.entries[i - 1]); close() },
                onDismiss = close,
            )
        }
        is ListDialog.Menu -> ListMenu(dialog.list, close,
            onEdit = { onDialog(ListDialog.EditList(dialog.list)) },
            onAccess = { onDialog(ListDialog.Access(dialog.list, isNew = false)) },
            onArchive = {
                lists.updateList(dialog.list.copy(archived = !dialog.list.archived))
                close()
                onSaved(if (dialog.list.archived) "Przywrócono z archiwum" else "Przeniesiono do archiwum", "Lista „${dialog.list.name}”.")
            },
            onDelete = { onDialog(ListDialog.DeleteList(dialog.list)) },
        )
        is ListDialog.Close -> CloseDialog(ui, dialog.list, close,
            onFinish = {
                if (dialog.list.look.finish == ListFinish.RESET) lists.uncheckAll(dialog.list.id) else lists.clearChecked(dialog.list.id)
                close()
                onSaved(if (dialog.list.look.finish == ListFinish.RESET) "Odznaczono wszystko" else "Wyczyszczono ${dialog.list.look.doneLabel}", "Zostają w pamięci — dodasz je jednym stuknięciem.")
            },
            onArchive = {
                lists.updateList(dialog.list.copy(archived = !dialog.list.archived))
                close()
                if (!dialog.list.archived) onClosed()
                onSaved(if (dialog.list.archived) "Przywrócono z archiwum" else "Przeniesiono do archiwum", "Lista „${dialog.list.name}”.")
            },
            onCopy = {
                val name = (dialog.list.name.take(52) + " (kopia)")
                val id = lists.copyList(dialog.list, name, ui.data.itemsOf(dialog.list.id))
                close()
                onCreated(id)
                onSaved("Zrobiono kopię", "„$name” — nic nie jest odhaczone.")
            },
            onDelete = { onDialog(ListDialog.DeleteList(dialog.list)) },
        )
        is ListDialog.Memory -> MemoryDialog(ui, dialog.list, lists, close, onNew = { onDialog(ListDialog.Item(dialog.list, null)) }, onSaved = onSaved)
        is ListDialog.DeleteList -> DeleteListDialog(ui, dialog.list, close,
            onDelete = { lists.deleteList(dialog.list); close(); onSaved("Usunięto listę", "„${dialog.list.name}”.") },
            onArchive = { lists.updateList(dialog.list.copy(archived = true)); close(); onSaved("Przeniesiono do archiwum", "Lista „${dialog.list.name}”.") },
        )
        is ListDialog.Item -> ItemDialog(ui, dialog.list, lists, dialog.item, close,
            onUngroup = { item ->
                val group = ui.data.item(item.parentId)?.values?.name.orEmpty()
                lists.ungroup(item)
                close()
                onSaved("Wyjęto z grupy", "„${item.values.name}” jest znów na liście" + if (group.isNotEmpty()) " (było w „$group”)." else ".")
            },
            onSave = { values, remember ->
                val item = dialog.item
                close()
                if (item == null) addOrAsk(ui, dialog.list, lists, values, remember, onDialog, onSaved) else lists.edit(item, values, remember)
            },
            onDelete = { item -> lists.delete(item); close(); onSaved("Usunięto", "„${item.values.name}”.") },
        )
        is ListDialog.Photos -> PhotoViewer(ui, dialog.list, lists, dialog.item.id, dialog.start, close)
        is ListDialog.OpenFile -> FileOpener(ui, lists, dialog.file, close)
        is ListDialog.ToGroup -> ToGroupDialog(ui, dialog.list, dialog.ids, close) { groupId, name ->
            val items = dialog.ids.mapNotNull { ui.data.item(it) }
            val groupName = groupId?.let { ui.data.item(it)?.values?.name } ?: name.trim()
            val undo = lists.toGroup(dialog.list, items, groupId, name)
            ui.prefs.marked.clear()
            groupId?.let { ui.prefs.setSubOpen(it, true) }
            close()
            onUndoable("${countWord(items.size, dialog.list)} w grupie „$groupName”", "", undo)
        }
        is ListDialog.DeleteMany -> {
            val items = dialog.ids.mapNotNull { ui.data.item(it) }
            LuxuryPopup(onDismiss = close) {
                Text("Usunąć zaznaczone (${items.size})?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
                Text(
                    items.take(4).joinToString(", ") { it.values.name } + (if (items.size > 4) " i ${items.size - 4} więcej" else "") + ". Znikną z listy u wszystkich.",
                    fontSize = 13.sp, color = Muted,
                )
                PopupButtons {
                    DangerButton("Usuń (${items.size})", {
                        lists.deleteAll(items)
                        ui.prefs.marked.clear()
                        close()
                        onSaved("Usunięto", countWord(items.size, dialog.list) + ".")
                    })
                    FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = close)
                }
            }
        }
        is ListDialog.ItemMenu -> ItemMenu(ui, dialog.list, dialog.item, close,
            onMove = { onDialog(ListDialog.ToGroup(dialog.list, listOf(dialog.item.id))) },
            onSubs = {
                ui.prefs.setSubOpen(dialog.item.id, true)
                ui.prefs.focusSub = dialog.item.id
                close()
            },
            onDetails = { onDialog(ListDialog.Item(dialog.list, dialog.item)) },
            onAccess = { onDialog(ListDialog.ItemAccess(dialog.list, dialog.item)) },
        )
        is ListDialog.ItemAccess -> AccessEditor(ui, dialog.list, isNew = false, item = dialog.item, onDismiss = close) { access ->
            lists.itemAccess(dialog.item, access)
            close()
            onSaved("Zapisano dostęp", "„${dialog.item.values.name}” — " + if (access.isEmpty()) "jak na liście." else "razem z grupą.")
        }
        is ListDialog.CheckAll -> {
            val open = ui.data.subItemsOf(dialog.item.id).filter { !it.checked && ui.canCheck(dialog.list, it) }
            val tasks = dialog.list.type == ListType.TASKS
            LuxuryPopup(onDismiss = close) {
                Text("„${dialog.item.values.name}” ma w grupie ${if (tasks) "niezrobione zadania" else "nieodhaczone pozycje"}", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
                Text(
                    "Zostało: " + open.take(3).joinToString(", ") { it.values.name } + if (open.size > 3) " i ${open.size - 3} więcej." else ".",
                    fontSize = 13.sp, color = Muted,
                )
                PopupButtons {
                    GoldButton(if (tasks) "Odhacz wszystko (${open.size + 1})" else "Odhacz razem z grupą") {
                        open.forEach { lists.check(it, true) }
                        lists.check(dialog.item, true)
                        close()
                    }
                    FramedButton(if (tasks) "Tylko to zadanie" else "Tylko „${dialog.item.values.name.take(20)}”", modifier = Modifier.fillMaxWidth()) {
                        lists.check(dialog.item, true)
                        close()
                    }
                    FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = close)
                }
            }
        }
        is ListDialog.CheckParent -> LuxuryPopup(onDismiss = close) {
            Text("Wszystko w grupie zrobione", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            Text("Odhaczyć też „${dialog.item.values.name}”?", fontSize = 14.sp, color = Muted)
            PopupButtons {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FramedButton("Nie", gold = false, modifier = Modifier.weight(1f), onClick = close)
                    Box(modifier = Modifier.weight(1f)) { GoldButton("Odhacz") { lists.check(dialog.item, true); close() } }
                }
            }
        }
        is ListDialog.Quantity -> LuxuryPopup(onDismiss = close) {
            Header(dialog.item.values.name, dialog.list.name)
            QuantityEditor(ui, dialog.list, lists, dialog.item.values, onCancel = close) { values, remember ->
                lists.edit(dialog.item, values, remember)
                close()
            }
        }
        is ListDialog.Conflict -> {
            val conflict = ui.data.openConflicts(dialog.item.id).firstOrNull()
            if (conflict == null) close() else ConflictDialog(ui, dialog.list, dialog.item, conflict, close,
                onDecide = { decision -> onResolve(conflict.id, decision); close() },
                onRemove = { onResolve(conflict.id, ConflictDecision(removeItem = true)); close() },
            )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(subtitle, fontSize = 12.sp, color = Muted)
    }
}

// --- boards 5 and 10: a new list (kind, ready-made pattern, own settings); who sees it is the next step ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListForm(ui: ListsUi, editing: ListInfo?, onDismiss: () -> Unit, onDone: (ListInfo) -> Unit) {
    var type by remember { mutableStateOf(editing?.type ?: ListType.SHOPPING) }
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var style by remember { mutableStateOf(editing?.look ?: ListStyle.SHOPPING) }
    var tried by remember { mutableStateOf(false) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text(if (editing == null) "Nowa lista" else "Ustawienia listy", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        if (editing == null) {
            LuxurySectionTitle("Rodzaj")
            ListType.entries.forEach { option ->
                Radio(option.label, typeDescription(option), option == type) { type = option }
            }
        }
        if (type == ListType.SHOPPING) {
            LuxurySectionTitle("Wzór")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListPreset.entries.forEach { preset ->
                    Chip(preset.label, style.preset == preset) { if (style.preset != preset) style = ListStyle.of(preset) }
                }
            }
            Text(presetDescription(style.preset), fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
            if (style.preset == ListPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = style.openLabel, onValueChange = { style = style.copy(openLabel = it.take(30)) }, singleLine = true,
                        label = { Text("Sekcja otwarta") }, placeholder = { Text("do zobaczenia") }, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = style.doneLabel, onValueChange = { style = style.copy(doneLabel = it.take(30)) }, singleLine = true,
                        label = { Text("Sekcja zrobiona") }, placeholder = { Text("zobaczone") }, modifier = Modifier.weight(1f),
                    )
                }
                SwitchRow(
                    label = "Ilość przy pozycjach", checked = style.quantity, enabled = true,
                    below = { Text("np. „2 szt.”, „3 kg”", fontSize = 12.sp, color = Muted) },
                ) { style = style.copy(quantity = it) }
                Text("Przycisk na dole listy", fontSize = 13.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Wyczyść zrobione", style.finish == ListFinish.HISTORY) { style = style.copy(finish = ListFinish.HISTORY) }
                    Chip("Odznacz wszystko", style.finish == ListFinish.RESET) { style = style.copy(finish = ListFinish.RESET) }
                }
                Text(
                    if (style.finish == ListFinish.HISTORY) "Zrobione znikają z listy, zostają w pamięci. Lista jednorazowa."
                    else "Wszystko wraca na listę jako nieodhaczone. Lista wielokrotnego użytku.",
                    fontSize = 12.sp, color = Muted,
                )
                Text("Ikona", fontSize = 13.sp, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListIcon.entries.forEach { icon ->
                        val on = style.icon == icon
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (on) Luxury.GoldBase else Outline, RoundedCornerShape(12.dp))
                                .background(if (on) Luxury.GoldBase.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable(role = Role.RadioButton, onClickLabel = "Ikona") { style = style.copy(icon = icon) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(styleIcon(icon), contentDescription = null, tint = if (on) Luxury.GoldText else Muted, modifier = Modifier.size(22.dp)) }
                    }
                }
            }
            if (editing == null && style.preset == ListPreset.SHOPPING) Locked("Lista z planu posiłków", "wkrótce, razem z modułem Posiłki")
        }
        LuxurySectionTitle("Nazwa")
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(60) }, singleLine = true, isError = tried && name.isBlank(),
            placeholder = { Text(namePlaceholder(type, style.preset)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (tried && name.isBlank()) FieldError("Wpisz nazwę listy.")
        if (editing == null) Text("Kto widzi i co może — w następnym kroku.", fontSize = 12.sp, color = Muted)
        PopupButtons {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                Box(modifier = Modifier.weight(1f)) {
                    GoldButton(if (editing == null) "Dalej" else "Zapisz") {
                        tried = true
                        val clean = style.copy(
                            openLabel = style.openLabel.trim().ifEmpty { ListStyle.CUSTOM.openLabel },
                            doneLabel = style.doneLabel.trim().ifEmpty { ListStyle.CUSTOM.doneLabel },
                        )
                        if (name.isNotBlank()) {
                            val base = editing ?: ListInfo(id = "", type = type, name = "", createdBy = ui.me.id)
                            onDone(base.copy(name = name.trim(), style = clean.takeIf { type == ListType.SHOPPING }))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Radio(title: String, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, if (on) Luxury.GoldBase else Outline, RoundedCornerShape(14.dp))
            .background(if (on) Luxury.GoldBase.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, Luxury.GoldBase, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight))
        }
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (text.isNotEmpty()) Text(text, fontSize = 12.sp, color = Muted)
        }
    }
}

private fun typeDescription(type: ListType) = when (type) {
    ListType.SHOPPING -> "zakupy, pakowanie albo własna · odhaczanie"
    ListType.TASKS -> "dla kogo · opcjonalny termin · pilne"
    ListType.DEADLINES -> "data albo pilne · sortowanie po dacie"
}

private fun presetDescription(preset: ListPreset) = when (preset) {
    ListPreset.SHOPPING -> "Do kupienia / kupione · ilość i jednostki · kupione zostają na liście, „Wyczyść kupione” zdejmuje je · pamięć produktów i sumowanie duplikatów."
    ListPreset.PACKING -> "Do spakowania / spakowane · ilość · „Odznacz wszystko” — ta sama lista na następny wyjazd."
    ListPreset.CUSTOM -> "Sam nazywasz sekcje i wybierasz, jak lista działa. Np. „Miejsca do zobaczenia”, „Prezenty”, „Książki do przeczytania”."
}

private fun namePlaceholder(type: ListType, preset: ListPreset) = when (type) {
    ListType.SHOPPING -> when (preset) {
        ListPreset.SHOPPING -> "np. Biedronka — tydzień"
        ListPreset.PACKING -> "np. Wakacje — walizka"
        ListPreset.CUSTOM -> "np. Kraków — do zobaczenia"
    }
    ListType.TASKS -> "np. Dom — porządki"
    ListType.DEADLINES -> "np. Opłaty i przeglądy"
}

// --- board 15: who sees the list and what they may do ---

@Composable
private fun AccessEditor(
    ui: ListsUi,
    list: ListInfo,
    isNew: Boolean,
    item: ListItem? = null,
    onDismiss: () -> Unit,
    onSave: (List<ListAccess>) -> Unit,
) {
    val roles = ui.roles.filterNot { it.owner }
    val creator = list.createdBy.ifEmpty { ui.me.id }
    var access by remember {
        mutableStateOf(
            when {
                item != null && item.access.isNotEmpty() -> item.access
                list.access.isNotEmpty() -> list.access
                list.everyone || isNew -> roles.map { ListAccess(roleId = it.id, level = AccessLevel.EDIT_ALL) }
                else -> roles.map { ListAccess(roleId = it.id, level = AccessLevel.NONE) } +
                    list.memberIds.filter { it != creator }.map { ListAccess(userId = it, level = AccessLevel.EDIT_ALL) }
            },
        )
    }
    var adding by remember { mutableStateOf(false) }
    fun roleLevel(roleId: String) = access.firstOrNull { it.userId == null && it.roleId == roleId }?.level ?: AccessLevel.NONE
    fun set(entry: ListAccess) {
        access = access.filterNot { (entry.userId != null && it.userId == entry.userId) || (entry.userId == null && it.userId == null && it.roleId == entry.roleId) } + entry
    }
    LuxuryPopup(onDismiss = onDismiss) {
        Column {
            if (item == null) {
                Text("Dostęp do listy", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
                Text("${list.name} · ustala twórca listy", fontSize = 12.sp, color = Muted)
            } else {
                Text(if (list.type == ListType.TASKS) "Inny dostęp do tego zadania" else "Inny dostęp do tej pozycji", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
                Text("${item.values.name} · razem z grupą", fontSize = 12.sp, color = Muted)
            }
        }
        if (item != null) {
            MessageBar(MessageKind.INFO, "Tylko zawęża dostęp z listy", "Nikt nie dostanie tu więcej niż na liście „${list.name}”. Kto nie widzi, temu zniknie razem z grupą.")
        }
        Text("Poziomy (każdy zawiera poprzedni): widzi → odhacza → dodaje → edytuje i usuwa cudze", fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
        if (roles.isEmpty()) {
            MessageBar(MessageKind.STATUS, "Role pojawią się po połączeniu", "Bez sieci lista będzie dla całej społeczności; dostęp zmienisz później.", icon = AppIcons.Offline)
        } else {
            LuxurySectionTitle("Role")
            ui.roles.filter { it.owner }.forEach { owner ->
                AccessRow(owner.name, "zawsze wszystko", null) {}
            }
            roles.forEach { role ->
                LuxuryDivider()
                AccessRow(role.name, null, roleLevel(role.id)) { set(ListAccess(roleId = role.id, level = it)) }
            }
            LuxurySectionTitle("Osoby · ważniejsze niż rola")
            val creatorMember = ui.member(creator)
            AccessRow(
                (creatorMember?.name ?: "Ty") + if (creator == ui.me.id) " (Ty)" else "", "twórca listy · wszystko", null,
                avatar = creatorMember,
            ) {}
            access.filter { it.userId != null }.forEach { entry ->
                val member = ui.member(entry.userId) ?: return@forEach
                val byRole = roleLevel(member.role.id)
                val hint = "rola ${member.role.name} → " + when {
                    entry.level.ordinal > byRole.ordinal -> "tutaj więcej"
                    entry.level.ordinal < byRole.ordinal -> "tutaj mniej"
                    else -> "tyle samo"
                }
                LuxuryDivider()
                AccessRow(member.name + if (member.id == ui.me.id) " (Ty)" else "", hint, entry.level, avatar = member, onRemove = {
                    access = access.filterNot { it.userId == member.id }
                }) { set(entry.copy(level = it)) }
            }
            val free = ui.members.filter { m -> m.id != creator && !m.role.owner && access.none { it.userId == m.id } }
            if (free.isNotEmpty()) {
                Text(
                    "+ Dodaj osobę", fontSize = 14.sp, color = Luxury.GoldText,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { adding = !adding }.padding(vertical = 8.dp),
                )
                if (adding) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        free.forEach { member ->
                            PersonChip(member.name, member.avatar, false) {
                                set(ListAccess(userId = member.id, level = roleLevel(member.role.id)))
                                adding = false
                            }
                        }
                    }
                }
            }
        }
        Text(
            "Lista może tylko zawęzić to, na co pozwala tabela uprawnień społeczności (np. „Zadania dla innych” dalej wymaga zatwierdzenia).",
            fontSize = 11.sp, color = Faint, lineHeight = 15.sp,
        )
        PopupButtons {
            if (item != null && item.access.isNotEmpty()) {
                FramedButton("Jak na liście (usuń inny dostęp)", gold = false, modifier = Modifier.fillMaxWidth()) { onSave(emptyList()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FramedButton(if (isNew) "Wstecz" else "Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                Box(modifier = Modifier.weight(1f)) {
                    GoldButton(if (isNew) "Utwórz" else "Zapisz dostęp") {
                        if (item != null && roles.isEmpty()) onDismiss() else onSave(if (roles.isEmpty()) emptyList() else access)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessRow(
    title: String,
    hint: String?,
    level: AccessLevel?,
    avatar: pl.ambif.ambify.shared.Member? = null,
    onRemove: (() -> Unit)? = null,
    onPick: (AccessLevel) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        avatar?.let { PersonAvatar(it.name, it.avatar, size = 28.dp, zoom = AvatarZoom.HOLD) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            hint?.let { Text(it, fontSize = 11.sp, color = Muted) }
        }
        if (level == null) {
            Text("wszystko", fontSize = 13.sp, color = Faint)
        } else {
            Box {
                Row(
                    modifier = Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Luxury.GoldLink, RoundedCornerShape(18.dp))
                        .clickable(onClickLabel = "Zmień dostęp: $title") { open = true }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(level.label, fontSize = 13.sp, color = if (level == AccessLevel.NONE) Muted else Luxury.GoldText)
                    Icon(AppIcons.ExpandMore, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    AccessLevel.entries.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label) }, onClick = { onPick(option); open = false })
                    }
                }
            }
        }
        onRemove?.let { remove ->
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Usuń: $title", onClick = remove), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Close, contentDescription = "Usuń: $title", tint = Muted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// --- board 12: the list is done — what now? ---

@Composable
private fun DoneDialog(ui: ListsUi, list: ListInfo, lists: ListsController, onDismiss: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val items = ui.data.itemsOf(list.id)
    val newest = items.maxOfOrNull { it.addedAt } ?: 0
    val shopping = list.type == ListType.SHOPPING
    val keep = when {
        !shopping -> "Zostaw"
        list.look.finish == ListFinish.RESET -> "Odznacz wszystko na następny raz"
        else -> "Zostaw i wyczyść ${list.look.doneLabel}"
    }
    val answer = { ui.prefs.answerDone(list.id, newest) }
    LuxuryPopup(onDismiss = { answer(); onDismiss() }) {
        LuxurySectionTitle(if (shopping && list.look.preset == ListPreset.SHOPPING) "Wszystko kupione" else "Wszystko zrobione", color = MessageKind.SUCCESS.color)
        Text("Co z listą „${list.name}”?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text(
            "Odhaczono ostatnią pozycję. Możesz usunąć listę albo ją zostawić" + when {
                !shopping -> "."
                list.look.finish == ListFinish.RESET -> " — wszystko wróci jako nieodhaczone."
                else -> " — " + list.look.doneLabel + " znikną z listy, ale zostaną w pamięci."
            },
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp,
        )
        GoldButton(keep) {
            answer()
            if (shopping) {
                if (list.look.finish == ListFinish.RESET) lists.uncheckAll(list.id) else lists.clearChecked(list.id)
            }
            onDismiss()
        }
        FramedButton("Archiwizuj", modifier = Modifier.fillMaxWidth()) { answer(); onArchive() }
        DangerButton("Usuń listę", { answer(); onDelete() })
        if (shopping) {
            Text(
                "Nie pytaj więcej o tę listę", fontSize = 13.sp, color = Luxury.GoldText, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) {
                    answer()
                    ui.prefs.setNeverAskDone(list.id)
                    onDismiss()
                }.padding(10.dp),
            )
        }
    }
}

// --- board 14: the same thing twice ---

@Composable
private fun DuplicateDialog(ui: ListsUi, dialog: ListDialog.Duplicate, lists: ListsController, onDismiss: () -> Unit) {
    val list = dialog.list
    val existing = dialog.existing
    val adding = dialog.values
    val units = ui.data.allUnits
    val quantity = list.type == ListType.SHOPPING && list.look.quantity
    val addingText = Lists.quantityText(adding, units)
    val sum = if (quantity) Lists.sum(existing.values, adding) else null
    var other by remember { mutableStateOf(false) }
    var variant by remember { mutableStateOf("") }
    LuxuryPopup(onDismiss = onDismiss) {
        LuxurySectionTitle("Jest już na liście")
        Text(nameText(adding, suffix = if (addingText.isNotEmpty()) " — dodajesz $addingText" else " — dodajesz jeszcze raz"), fontSize = 18.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        ItemCard(ui, existing, "dodał(a) ${ui.name(existing.addedBy)} · ${time(existing.addedAt)}", if (quantity) Lists.quantityText(existing.values, units) else "")
        if (sum != null) {
            GoldButton("Dodaj do istniejącej → razem ${Lists.quantityText(sum, units)}") {
                lists.edit(existing, sum.copy(name = existing.values.name, variant = existing.values.variant))
                onDismiss()
            }
        }
        if (quantity && adding.quantity != null) {
            FramedButton("Zamień na $addingText", modifier = Modifier.fillMaxWidth()) {
                lists.edit(existing, existing.values.copy(quantity = adding.quantity, unit = adding.unit, packSize = adding.packSize))
                onDismiss()
            }
        }
        if (list.type == ListType.SHOPPING) {
            if (other) {
                OutlinedTextField(
                    value = variant, onValueChange = { variant = it.take(Lists.MAX_VARIANT) }, singleLine = true,
                    label = { Text("Rodzaj nowej pozycji") }, placeholder = { Text("np. 2%, bez laktozy") }, modifier = Modifier.fillMaxWidth(),
                )
                VariantChips(ui.data.variantsOf(adding.name).filter { it != existing.values.variant }, variant) { variant = it }
                val typed = variant.trim()
                GoldButton(if (typed.isEmpty()) "Wpisz rodzaj" else "Dodaj „${adding.name} $typed” osobno", enabled = typed.isNotEmpty() && typed != existing.values.variant) {
                    lists.addItem(list.id, adding.copy(variant = typed), dialog.remember)
                    onDismiss()
                }
            } else {
                FramedButton("To inny rodzaj…", gold = false, modifier = Modifier.fillMaxWidth()) { other = true }
            }
        } else {
            FramedButton("Dodaj mimo to", gold = false, modifier = Modifier.fillMaxWidth()) {
                lists.addItem(list.id, adding, dialog.remember)
                onDismiss()
            }
        }
        PopupButtons {
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun MergeDialog(ui: ListsUi, dialog: ListDialog.Merge, lists: ListsController, onDismiss: () -> Unit) {
    val (first, second) = listOf(dialog.first, dialog.second).sortedBy { it.addedAt }
    val units = ui.data.allUnits
    val quantity = dialog.list.look.quantity
    val sum = if (quantity) Lists.sum(first.values, second.values) else null
    fun added(item: ListItem) = time(item.addedAt) + if (item.id in ui.waitingItems) " · czeka na wysłanie" else ""
    LuxuryPopup(onDismiss = onDismiss) {
        LuxurySectionTitle("Podwójnie?")
        Text(nameText(first.values, suffix = " jest na liście dwa razy"), fontSize = 18.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text(
            if (first.addedBy != second.addedBy) "Dwie osoby dodały to niezależnie (np. jedna bez sieci). Połączyć w jedną pozycję?"
            else "Ta sama pozycja jest dodana dwa razy. Połączyć w jedną?",
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp,
        )
        ItemCard(ui, first, "${ui.name(first.addedBy)} · ${added(first)}", if (quantity) Lists.quantityText(first.values, units) else "")
        ItemCard(ui, second, "${ui.name(second.addedBy)} · ${added(second)}", if (quantity) Lists.quantityText(second.values, units) else "")
        if (sum != null) {
            GoldButton("Połącz: razem ${Lists.quantityText(sum, units)}") { lists.merge(first, second, sum); onDismiss() }
        }
        val firstText = Lists.quantityText(first.values, units)
        FramedButton(if (quantity && firstText.isNotEmpty()) "Połącz: zostaw $firstText" else "Połącz w jedną", modifier = Modifier.fillMaxWidth()) {
            lists.delete(second)
            onDismiss()
        }
        FramedButton("To różne rzeczy — zostaw obie", gold = false, modifier = Modifier.fillMaxWidth()) {
            ui.prefs.markDifferent(first.id, second.id)
            onDismiss()
        }
        Text("Może każdy, kto edytuje listę. Znacznik „Podwójnie?” znika po decyzji.", fontSize = 11.sp, color = Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ItemCard(ui: ListsUi, item: ListItem, meta: String, amount: String) {
    val member = ui.member(item.addedBy)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Outline, RoundedCornerShape(16.dp)).background(Luxury.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PersonAvatar(member?.name ?: "?", member?.avatar, size = 30.dp, zoom = AvatarZoom.HOLD)
        Column(modifier = Modifier.weight(1f)) {
            Text(nameText(item.values), fontSize = 15.sp)
            Text(meta, fontSize = 12.sp, color = Muted)
        }
        if (amount.isNotEmpty()) Text(amount, fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariantChips(variants: List<String>, current: String, onPick: (String) -> Unit) {
    if (variants.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        variants.take(8).forEach { Chip(it, it == current.trim()) { onPick(it) } }
    }
    Text("Podpowiedzi: rodzaje używane wcześniej w społeczności.", fontSize = 11.sp, color = Muted, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont)
}

// --- board 17: "Sortuj" of one level ---

/** Board 17, "Podlisty sortuj tak samo" under the options of a list. */
private class InheritToggle(val on: Boolean, val text: String, val onToggle: () -> Unit)

@Composable
private fun SortDialog(
    title: String,
    where: String,
    whereText: String,
    options: List<Triple<String, String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    inherit: InheritToggle? = null,
) {
    LuxuryPopup(onDismiss = onDismiss) {
        Text(title, fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Luxury.GoldLink, RoundedCornerShape(14.dp))
                .background(Luxury.GoldBase.copy(alpha = 0.08f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Sort, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
            Column {
                Text(where, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(whereText, fontSize = 12.sp, color = Muted)
            }
        }
        Column {
            options.forEachIndexed { i, (label, text, on) ->
                if (i > 0) LuxuryDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton) { onPick(i) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, Luxury.GoldBase, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight))
                    }
                    Column {
                        Text(label, fontSize = 15.sp)
                        Text(text, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
        inherit?.let { toggle ->
            LuxuryDivider()
            SwitchRow("Grupy sortuj tak samo", checked = toggle.on, enabled = true,
                below = { Text(toggle.text, fontSize = 12.sp, color = Muted) }) { toggle.onToggle() }
        }
        Text("Tylko u Ciebie, zapamiętane na tym telefonie.", fontSize = 12.sp, color = Muted)
        PopupButtons {
            FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** A premium extra not available yet, or not bought: shown, never usable. */
@Composable
private fun Locked(title: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Outline, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.Star, contentDescription = null, tint = Faint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Faint, fontSize = 14.sp)
            Text(text, color = Faint, fontSize = 11.sp)
        }
    }
}

// --- board 16: ⋮ of an item ---

@Composable
private fun ItemMenu(
    ui: ListsUi, list: ListInfo, item: ListItem, onDismiss: () -> Unit, onMove: () -> Unit, onSubs: () -> Unit, onDetails: () -> Unit, onAccess: () -> Unit,
) {
    val tasks = list.type == ListType.TASKS
    val subs = ui.data.subItemsOf(item.id)
    LuxuryPopup(onDismiss = onDismiss) {
        Text(item.values.name, fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium, maxLines = 2)
        if (ui.canAddTo(list, item)) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onSubs), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (subs.isNotEmpty()) "Dodaj coś do tej grupy" else "Zrób z tego grupę", fontSize = 16.sp)
                    Text(
                        if (subs.isNotEmpty()) "rozwinie się, z polem do wpisania"
                        else if (tasks) "w środku wpiszesz kroki tego zadania" else "w środku wpiszesz, co do niej należy",
                        fontSize = 12.sp, color = Muted,
                    )
                }
                LuxuryOpenMark(size = 32.dp)
            }
            LuxuryDivider()
        }
        // A group itself does not go into another one (board 27).
        if (subs.isEmpty() && ui.canEdit(list, item) && list.type != ListType.DEADLINES) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onMove), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Przenieś do grupy…", fontSize = 16.sp)
                    Text("do istniejącej albo nowej · kilka naraz: przytrzymaj pozycję", fontSize = 12.sp, color = Muted)
                }
                LuxuryOpenMark(size = 32.dp)
            }
            LuxuryDivider()
        }
        MenuRow("Szczegóły", onDetails)
        if (ui.manageable(list)) {
            LuxuryDivider()
            MenuRow(if (tasks) "Inny dostęp do tego zadania" else "Inny dostęp do tej pozycji", onAccess)
        }
        PopupButtons {
            FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** "3 rzeczy", "1 zadanie" — what was marked (board 27). */
internal fun countWord(n: Int, list: ListInfo): String {
    val few = n % 10 in 2..4 && n % 100 !in 12..14
    return if (list.type == ListType.TASKS) "$n " + when { n == 1 -> "zadanie"; few -> "zadania"; else -> "zadań" }
    else "$n " + if (n == 1) "rzecz" else "rzeczy"
}

/** Board 27, "Dodaj do grupy": an existing group of the list or a new one, named here. */
@Composable
private fun ToGroupDialog(ui: ListsUi, list: ListInfo, ids: List<String>, onDismiss: () -> Unit, onDone: (String?, String) -> Unit) {
    val items = ids.mapNotNull { ui.data.item(it) }
    val groups = ui.data.itemsOf(list.id).filter { it.parentId == null && it.id !in ids && ui.data.subItemsOf(it.id).isNotEmpty() && ui.canAddTo(list, it) }
    var target by remember { mutableStateOf(if (groups.isEmpty()) NEW_GROUP else "") }
    var name by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Dodaj do grupy", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text(
            "${items.size} zaznaczone: " + items.take(4).joinToString(", ") { it.values.name } + if (items.size > 4) " i ${items.size - 4} więcej" else "",
            fontSize = 13.sp, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        Column {
            groups.forEach { group ->
                GroupOption(group.values.name, "istniejąca grupa · ${ui.data.subItemsOf(group.id).size} w środku", target == group.id) { target = group.id }
                LuxuryDivider()
            }
            GroupOption("Nowa grupa", "powstanie na liście w miejscu pierwszej zaznaczonej", target == NEW_GROUP) { target = NEW_GROUP }
        }
        if (target == NEW_GROUP) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Nazwa grupy") }, placeholder = { Text(if (list.type == ListType.TASKS) "np. Wymień żarówkę" else "np. Kosmetyczka") },
                isError = tried && name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Luxury.GoldBase, unfocusedBorderColor = Outline),
                modifier = Modifier.fillMaxWidth(),
            )
            if (tried && name.isBlank()) FieldError("Wpisz nazwę grupy.")
        }
        if (tried && target.isEmpty()) FieldError("Wybierz grupę albo „Nowa grupa”.")
        PopupButtons {
            GoldButton("Dodaj ${items.size} do grupy") {
                tried = true
                when {
                    target.isEmpty() -> Unit
                    target == NEW_GROUP -> if (name.isNotBlank()) onDone(null, name)
                    else -> onDone(target, "")
                }
            }
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

private const val NEW_GROUP = "#new"

@Composable
private fun GroupOption(title: String, text: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, Luxury.GoldBase, CircleShape), contentAlignment = Alignment.Center) {
            if (on) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Luxury.GoldLight))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(text, fontSize = 12.sp, color = Muted)
        }
    }
}

// --- premium "Linki" (board 13): ogniwo = link, opens the browser ---

@Composable
private fun LinksEditor(links: List<ItemLink>, editable: Boolean, onChange: (List<ItemLink>) -> Unit) {
    val uri = LocalUriHandler.current
    var adding by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    LuxurySectionTitle("Linki · ${links.size}/${Lists.MAX_LINKS}")
    links.forEach { link ->
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Otwórz w przeglądarce", role = Role.Button) { runCatching { uri.openUri(link.url) } }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Link, contentDescription = null, tint = Luxury.GoldText, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(link.title.ifBlank { Lists.domain(link.url) }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Lists.domain(link.url)} · link — otwiera przeglądarkę", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (editable) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = "Usuń link: ${Lists.domain(link.url)}") { onChange(links - link) },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, contentDescription = "Usuń link: ${Lists.domain(link.url)}", tint = Muted, modifier = Modifier.size(16.dp)) }
            }
        }
    }
    if (!editable) return
    if (adding) {
        val bad = tried && !Lists.isLink(url.trim())
        OutlinedTextField(
            value = url, onValueChange = { url = it.trim() }, singleLine = true, isError = bad,
            label = { Text("Adres (wklej)") }, placeholder = { Text("https://…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        if (bad) FieldError("Wklej adres zaczynający się od http:// lub https://.")
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(60) }, singleLine = true,
            label = { Text("Podpis (opcjonalnie)") }, placeholder = { Text("np. Instrukcja producenta") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f)) { adding = false; url = ""; title = ""; tried = false }
            Box(modifier = Modifier.weight(1f)) {
                GoldButton("Dodaj link") {
                    tried = true
                    val clean = url.trim()
                    if (Lists.isLink(clean)) {
                        if (links.none { it.url == clean }) onChange(links + ItemLink(clean, title.trim()))
                        adding = false; url = ""; title = ""; tried = false
                    }
                }
            }
        }
    } else if (links.size < Lists.MAX_LINKS) {
        FramedButton("+ Link", modifier = Modifier.fillMaxWidth()) { adding = true }
    } else {
        Text("Najwyżej ${Lists.MAX_LINKS} linków przy jednej pozycji.", fontSize = 12.sp, color = Muted)
    }
}

// --- board 24: forms of a unit of the community (1 / 2–4 / 5+) ---

/**
 * A new unit ([editing] without a name) or "Popraw odmianę" of one the community has. The forms are guessed by the
 * ending of its name as it is typed; a corrected field gets a gold frame and stays as typed.
 */
@Composable
private fun UnitFormsDialog(list: ListInfo, itemName: String, editing: ListUnit, onDismiss: () -> Unit, onSave: (ListUnit) -> Unit) {
    val isNew = editing.name.isEmpty()
    var word by remember { mutableStateOf(editing.name) }
    var fixed by remember { mutableStateOf<Map<Int, String>>(if (isNew || editing.forms.isEmpty()) emptyMap() else editing.forms.withIndex().associate { it.index to it.value }) }
    var tried by remember { mutableStateOf(false) }
    val guessed = Lists.guessForms(word)
    val forms = (0..2).map { fixed[it] ?: guessed[it] }
    val sample = itemName.ifBlank { "Ogórki kiszone" }
    LuxuryPopup(onDismiss = onDismiss) {
        Column {
            Text(if (isNew) "Nowa jednostka" else "Odmiana: „${editing.name}”", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            Text(if (isNew) "${list.name} · własna jednostka" else "Przytrzymany chip jednostki → „Popraw odmianę”", fontSize = 12.sp, color = Muted)
        }
        if (isNew) {
            LuxurySectionTitle("Nazwa (jak dla 1 sztuki)")
            OutlinedTextField(
                value = word, onValueChange = { word = it.take(Lists.MAX_UNIT); fixed = emptyMap() }, singleLine = true,
                isError = tried && word.isBlank(), placeholder = { Text("np. wiaderko") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LuxurySectionTitle("Odmiana przy liczbach")
        listOf("1", "2–4", "5+").forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, fontSize = 15.sp, color = Muted, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                OutlinedTextField(
                    value = forms[i], onValueChange = { fixed = fixed + (i to it.take(Lists.MAX_UNIT)) }, singleLine = true,
                    isError = tried && forms[i].isBlank(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Luxury.GoldBase,
                        unfocusedBorderColor = if (i in fixed) Luxury.GoldLink else Outline,
                    ),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Forma przy liczbie $label" },
                )
            }
        }
        MessageBar(
            MessageKind.INFO, "Zgadujemy po końcówce słowa",
            if (isNew) "Gdy coś brzmi źle (np. „woreki”), po prostu popraw." else "Jednostki dopisane przed tą wersją dostały formy zgadnięte automatycznie. Popraw, jeśli coś brzmi źle.",
        )
        LuxurySectionTitle("Tak będzie na liście")
        listOf(1, 3, 8).forEachIndexed { i, n ->
            Row {
                Text(sample, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$n ${forms[i]}", fontSize = 15.sp, color = Luxury.GoldText)
            }
        }
        Text("Odmiana jest wspólna dla całej społeczności — poprawiasz ją raz.", fontSize = 12.sp, color = Faint)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onDismiss)
            Box(modifier = Modifier.weight(1f)) {
                GoldButton("Zapisz") {
                    tried = true
                    val name = word.trim()
                    val clean = forms.map { it.trim() }
                    if (name.isNotEmpty() && clean.all { it.isNotEmpty() }) {
                        onSave(ListUnit(name, clean, editing.step.takeIf { !isNew } ?: 1.0, editing.packUnit))
                    }
                }
            }
        }
    }
}

// --- list options and deleting (Komunikaty canvas, board 6) ---

@Composable
private fun ListMenu(list: ListInfo, onDismiss: () -> Unit, onEdit: () -> Unit, onAccess: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    LuxuryPopup(onDismiss = onDismiss) {
        Text(list.name, fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        MenuRow(if (list.type == ListType.SHOPPING) "Nazwa i ustawienia" else "Zmień nazwę", onEdit)
        LuxuryDivider()
        MenuRow("Dostęp do listy", onAccess)
        LuxuryDivider()
        MenuRow(if (list.archived) "Przywróć z archiwum" else "Archiwizuj", onArchive)
        LuxuryDivider()
        MenuRow("Usuń listę", onDelete, color = MessageKind.ERROR.color)
        PopupButtons {
            FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit, color: Color = Luxury.Text) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f))
        LuxuryOpenMark(size = 32.dp)
    }
}

/** Board 20: "Zamknij" — what to do with the list; archive and delete only for who manages it. */
@Composable
private fun CloseDialog(ui: ListsUi, list: ListInfo, onDismiss: () -> Unit, onFinish: () -> Unit, onArchive: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val items = ui.data.itemsOf(list.id)
    val open = items.count { !it.checked }
    val done = items.size - open
    val things = list.type == ListType.SHOPPING
    val manage = ui.manageable(list)
    LuxuryPopup(onDismiss = onDismiss) {
        Text("Zamknij listę", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text(
            list.name + if (things) " · $open ${list.look.openLabel} · $done ${list.look.doneLabel}" else " · $open otwarte",
            fontSize = 12.sp, color = Muted,
        )
        Column {
            if (things && done > 0 && ui.canCheck(list)) {
                val reset = list.look.finish == ListFinish.RESET
                CloseRow(
                    AppIcons.Close,
                    if (reset) "Odznacz wszystko" else "Wyczyść ${list.look.doneLabel}",
                    if (reset) "wszystko wraca jako nieodhaczone, na następny raz" else "znikają z listy, zostają w pamięci",
                    onClick = onFinish,
                )
                LuxuryDivider()
            }
            if (manage) {
                CloseRow(AppIcons.Archive, if (list.archived) "Przywróć z archiwum" else "Archiwizuj",
                    if (list.archived) "wraca na ekran list" else "znika z ekranu list; wraca z „Archiwum”", onClick = onArchive)
                LuxuryDivider()
            }
            if (ui.canCreate) {
                CloseRow(AppIcons.Copy, "Zrób kopię", "nowa lista z tymi samymi pozycjami, nic odhaczone", onClick = onCopy)
                LuxuryDivider()
            }
            CloseRow(AppIcons.Share, "Wyślij jako tekst", "co zostało do zrobienia — SMS, WhatsApp, e-mail…") {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, list.name)
                    putExtra(android.content.Intent.EXTRA_TEXT, listText(ui, list))
                }
                context.startActivity(android.content.Intent.createChooser(send, "Wyślij listę"))
                onDismiss()
            }
            if (manage) {
                LuxuryDivider()
                CloseRow(AppIcons.Trash, "Usuń listę", "u wszystkich — z potwierdzeniem", color = MessageKind.ERROR.color, onClick = onDelete)
            }
        }
        if (!manage) Text("Archiwizować i usuwać może twórca listy (${ui.name(list.createdBy)}) albo Admin.", fontSize = 11.sp, color = Faint)
        PopupButtons {
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun CloseRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, color: Color = Luxury.Text, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (color == Luxury.Text) Luxury.GoldText else color, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, fontSize = 16.sp, color = color)
            Text(text, fontSize = 12.sp, color = Muted)
        }
    }
}

/** "Wyślij jako tekst": what is still open, one line each ("• Mleko 3,2% — 1 l"). */
internal fun listText(ui: ListsUi, list: ListInfo): String {
    val open = sorted(ui, list, ui.data.itemsOf(list.id).filter { !it.checked }, ui.prefs.itemSort(list)).flatMap { it.items }
    val lines = open.map { item ->
        val extra = when (list.type) {
            ListType.SHOPPING -> if (list.look.quantity) Lists.quantityText(item.values, ui.data.allUnits) else ""
            ListType.TASKS -> listOfNotNull(
                if (item.values.urgent) "PILNE" else item.values.dueDate?.let { dateWords(LocalDate.parse(it)) },
                item.values.assigneeId?.let { ui.name(it) },
            ).joinToString(", ")
            ListType.DEADLINES -> if (item.values.urgent) "PILNE" else item.values.dueDate?.let { LocalDate.parse(it).let { d -> "${d.dayOfMonth}.${d.monthValue}.${d.year}" } }.orEmpty()
        }
        "• " + nameOf(item.values) + if (extra.isNotEmpty()) " — $extra" else ""
    }
    return list.name + "\n" + lines.ifEmpty { listOf("(nic do zrobienia)") }.joinToString("\n")
}

@Composable
private fun DeleteListDialog(ui: ListsUi, list: ListInfo, onDismiss: () -> Unit, onDelete: () -> Unit, onArchive: () -> Unit) {
    val items = ui.data.itemsOf(list.id).size
    val people = ui.people(list).size
    LuxuryPopup(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(AppIcons.Warning, contentDescription = null, tint = MessageKind.ERROR.color, modifier = Modifier.size(22.dp))
            LuxurySectionTitle("Nie da się cofnąć", color = MessageKind.ERROR.color)
        }
        Text("Usunąć listę „${list.name}”?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
        Text(
            "Lista zniknie u wszystkich ($people ${plural(people, "osoba", "osoby", "osób")}), razem z $items ${plural(items, "pozycją", "pozycjami", "pozycjami")} (pamięć produktów zostaje)." +
                if (!list.archived) " Jeśli chcesz ją tylko schować, wybierz „Archiwizuj”." else "",
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp,
        )
        PopupButtons {
            DangerButton("Usuń listę", onDelete)
            if (!list.archived) FramedButton("Archiwizuj", modifier = Modifier.fillMaxWidth(), onClick = onArchive)
            FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** The red button of what cannot be undone. */
@Composable
internal fun DangerButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, Luxury.Error),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Luxury.Error.copy(alpha = 0.16f)),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(label, color = MessageKind.ERROR.color, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont) }
}

// --- board 6: an item ---

private enum class ItemPage { MAIN, QUANTITY, WHO, REMINDER }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDialog(
    ui: ListsUi,
    list: ListInfo,
    lists: ListsController,
    item: ListItem?,
    onDismiss: () -> Unit,
    onSave: (ItemValues, Boolean) -> Unit,
    onDelete: (ListItem) -> Unit,
    onUngroup: (ListItem) -> Unit = {},
) {
    var values by remember { mutableStateOf(item?.values ?: ItemValues(dueDate = if (list.type == ListType.DEADLINES) null else null)) }
    var keepUnit by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(ItemPage.MAIN) }
    var tried by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var skipLink by remember { mutableStateOf<String?>(null) }
    val editable = item == null || ui.canEdit(list, item)
    val title = when (list.type) {
        ListType.SHOPPING -> if (item == null) "Nowa pozycja" else if (item.parentId != null) "Pozycja w grupie" else "Pozycja"
        ListType.TASKS -> if (item == null) "Nowe zadanie" else if (item.parentId != null) "Zadanie w grupie" else "Zadanie"
        ListType.DEADLINES -> if (item == null) "Nowy termin" else "Termin"
    }
    val nameError = tried && values.name.isBlank()
    val dateError = tried && list.type.needsDate && values.dueDate == null && !values.urgent
    val forOther = values.assigneeId != null && values.assigneeId != ui.me.id
    val pendingNote = forOther && ui.tasksForOthers == Mode.APPROVAL && values.assigneeId != item?.values?.assigneeId

    LuxuryPopup(onDismiss = onDismiss) {
        when (page) {
            ItemPage.QUANTITY -> {
                Header(values.name.ifBlank { "Ilość" }, list.name)
                QuantityEditor(ui, list, lists, values, onCancel = { page = ItemPage.MAIN }) { changed, keep ->
                    values = changed
                    keepUnit = keepUnit || keep
                    page = ItemPage.MAIN
                }
                return@LuxuryPopup
            }
            ItemPage.WHO -> {
                Header("Dla kogo", list.name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Bez osoby", values.assigneeId == null) { values = values.copy(assigneeId = null); page = ItemPage.MAIN }
                    ui.members.filter { it.id == ui.me.id || ui.tasksForOthers != Mode.DENIED }.forEach { member ->
                        PersonChip(if (member.id == ui.me.id) "${member.name} (Ty)" else member.name, member.avatar, values.assigneeId == member.id) {
                            values = values.copy(assigneeId = member.id)
                            page = ItemPage.MAIN
                        }
                    }
                }
                when (ui.tasksForOthers) {
                    Mode.APPROVAL -> Text("Zadanie dla innej osoby pojawi się po zatwierdzeniu przez Admina lub Ownera.", fontSize = 12.sp, color = Muted)
                    Mode.DENIED -> Text("Twoja rola nie może dawać zadań innym osobom.", fontSize = 12.sp, color = Muted)
                    else -> Unit
                }
                PopupButtons {
                    FramedButton("Wstecz", gold = false, modifier = Modifier.fillMaxWidth()) { page = ItemPage.MAIN }
                }
                return@LuxuryPopup
            }
            ItemPage.REMINDER -> {
                Header("Przypomnienie", values.name)
                ReminderEditor(ui, values.reminders, onCancel = { page = ItemPage.MAIN }) { reminders ->
                    values = values.copy(reminders = reminders)
                    page = ItemPage.MAIN
                }
                return@LuxuryPopup
            }
            ItemPage.MAIN -> Unit
        }
        Header(title, list.name)
        OutlinedTextField(
            value = values.name, onValueChange = { values = values.copy(name = it.take(Lists.MAX_NAME)) },
            label = { Text(if (list.type == ListType.SHOPPING) "Nazwa" else "Treść") },
            isError = nameError, singleLine = false, maxLines = 3, enabled = editable,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (nameError) FieldError("Wpisz nazwę.")
        Column {
            if (list.type == ListType.SHOPPING) {
                OutlinedTextField(
                    value = values.variant, onValueChange = { values = values.copy(variant = it.take(Lists.MAX_VARIANT)) },
                    label = { Text("Rodzaj (opcjonalnie)") }, placeholder = { Text("np. 3,2%, bez laktozy") }, singleLine = true, enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
                if (editable) VariantChips(ui.data.variantsOf(values.name), values.variant) { values = values.copy(variant = it) }
                if (list.look.quantity) {
                    FieldRow("Ilość", Lists.quantityText(values, ui.data.allUnits).ifEmpty { "dodaj…" }, enabled = editable) { page = ItemPage.QUANTITY }
                }
            }
            if (list.type == ListType.TASKS) {
                val who = ui.member(values.assigneeId)
                FieldRow("Dla kogo", who?.name ?: "bez osoby", avatar = who, enabled = editable) { page = ItemPage.WHO }
            }
            if (list.type != ListType.SHOPPING) {
                LuxuryDivider()
                SwitchRow(
                    label = "Pilne", checked = values.urgent, enabled = editable,
                    below = { Text("bez daty, na górze listy", fontSize = 12.sp, color = Muted) },
                ) { on -> values = if (on) values.copy(urgent = true, dueDate = null, recurrence = Recurrence()) else values.copy(urgent = false) }
            }
            if (list.type != ListType.SHOPPING && !values.urgent) {
                LuxuryDivider()
                FieldRow(if (list.type == ListType.DEADLINES) "Data" else "Termin", values.dueDate?.let { dateWords(LocalDate.parse(it)) } ?: if (list.type.needsDate) "wybierz…" else "bez terminu", error = dateError, enabled = editable) {
                    picking = "date"
                }
                if (dateError) FieldError("Termin musi mieć datę albo być pilny.")
                if (values.dueDate != null) {
                    LuxuryDivider()
                    FieldRow("Powtarzanie", if (values.recurrence.frequency == Frequency.NONE) "nie powtarza się" else describe(values.recurrence, LocalDate.parse(values.dueDate)), enabled = editable) {
                        picking = "repeat"
                    }
                    if (list.type == ListType.TASKS && editable) {
                        Text("Bez daty: zadanie bez terminu", fontSize = 11.sp, color = Luxury.GoldText,
                            modifier = Modifier.clickable { values = values.copy(dueDate = null, recurrence = Recurrence()) }.padding(vertical = 6.dp))
                    }
                }
            }
            LuxuryDivider()
            OutlinedTextField(
                value = values.note, onValueChange = { values = values.copy(note = it.take(Lists.MAX_NOTE)) },
                label = { Text("Notatka (opcjonalnie)") }, maxLines = 4, enabled = editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            // A web address pasted into the note: "Dodać jako link?" (premium "Linki").
            val pasted = Lists.linkIn(values.note)?.takeIf { url ->
                ui.links && editable && url != skipLink && values.links.none { it.url == url } && values.links.size < Lists.MAX_LINKS
            }
            if (pasted != null) {
                MessageBar(MessageKind.INFO, "W notatce jest link", "Dodać „${Lists.domain(pasted)}” jako link? Otworzy się jednym stuknięciem.", icon = AppIcons.Link)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FramedButton("Zostaw w notatce", gold = false, modifier = Modifier.weight(1f)) { skipLink = pasted }
                    Box(modifier = Modifier.weight(1f)) {
                        GoldButton("Dodaj jako link") {
                            values = values.copy(note = values.note.replace(pasted, "").replace(Regex("[ \\t]{2,}"), " ").trim(), links = values.links + ItemLink(pasted))
                        }
                    }
                }
            }
        }
        if (pendingNote) MessageBar(MessageKind.INFO, "Czeka na zatwierdzenie", "Zadanie dla ${ui.member(values.assigneeId)?.name ?: "tej osoby"} pojawi się, gdy zatwierdzi je Admin lub Owner.")

        LuxurySectionTitle("Dodatki premium")
        if (list.type != ListType.SHOPPING) {
            if (ui.reminders) {
                val mine = values.reminders
                FieldRow("Przypomnienie", if (mine.isEmpty()) "brak" else reminderWords(ui, mine), enabled = editable) { page = ItemPage.REMINDER }
                if (values.dueDate == null && mine.isNotEmpty()) Text("Przypomnienie działa, gdy jest termin.", fontSize = 11.sp, color = MessageKind.WARNING.color)
            } else {
                Locked("Przypomnienie", "powiadomienie lub budzik dla wybranych osób — w pakiecie premium")
            }
        }
        if (ui.links) {
            LinksEditor(values.links, editable) { values = values.copy(links = it) }
            FilesEditor(ui, list, lists, item)
        } else {
            Locked("Linki, zdjęcia i pliki", "ogniwo = link, aparat = zdjęcie, kartka = plik · w pakiecie premium")
        }
        if (list.type == ListType.DEADLINES) Locked("Dodaj do kalendarza", "premium · wkrótce, razem z modułem Kalendarz")
        if (list.type == ListType.TASKS) Locked("Przenieś do Misji", "zadanie za punkty — wkrótce, razem z modułem Misje")
        if (list.type == ListType.SHOPPING && list.look.preset == ListPreset.SHOPPING) Locked("Zakupy z planu posiłków", "wkrótce, razem z modułem Posiłki")

        item?.let {
            val changed = it.changedAt?.let { at -> " · ostatnia zmiana: ${ui.name(it.changedBy)} · ${time(at)}" }.orEmpty()
            Text("dodał(a) ${ui.name(it.addedBy)} · ${time(it.addedAt)}$changed", fontSize = 11.sp, color = Faint)
        }
        if (!editable) {
            Text("Tę pozycję zmienia jej autor albo ktoś z dostępem „edytuje i usuwa”.", fontSize = 12.sp, color = Muted)
            PopupButtons {
                FramedButton("Zamknij", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
            return@LuxuryPopup
        }
        if (item?.parentId != null) {
            // Board 27: back on the list itself.
            FramedButton("Wyjmij z grupy „${ui.data.item(item.parentId)?.values?.name.orEmpty().take(24)}”", gold = false, modifier = Modifier.fillMaxWidth()) { onUngroup(item) }
        }
        PopupButtons {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item != null) DangerButton("Usuń", { confirmDelete = true }, Modifier)
                else FramedButton("Anuluj", gold = false, onClick = onDismiss)
                Box(modifier = Modifier.weight(1f)) {
                    GoldButton("Zapisz") {
                        tried = true
                        if (values.name.isNotBlank() && !(list.type.needsDate && values.dueDate == null && !values.urgent)) {
                            onSave(values.copy(name = values.name.trim(), variant = values.variant.trim()), keepUnit)
                        }
                    }
                }
            }
        }
    }
    when (picking) {
        "date" -> DateDialog(values.dueDate?.let { LocalDate.parse(it) } ?: LocalDate.now(), onDismiss = { picking = null }) {
            values = values.copy(dueDate = it.toString())
        }
        "repeat" -> RecurrenceDialog(values.recurrence, LocalDate.parse(values.dueDate ?: LocalDate.now().toString()), onDismiss = { picking = null }) {
            values = values.copy(recurrence = it)
            picking = null
        }
    }
    if (confirmDelete && item != null) {
        LuxuryPopup(onDismiss = { confirmDelete = false }) {
            Text("Usunąć „${item.values.name}”?", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            Text("Pozycja zniknie z listy u wszystkich.", fontSize = 13.sp, color = Muted)
            PopupButtons {
                DangerButton("Usuń", { confirmDelete = false; onDelete(item) })
                FramedButton("Anuluj", gold = false, modifier = Modifier.fillMaxWidth()) { confirmDelete = false }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, avatar: pl.ambif.ambify.shared.Member? = null, error: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (error) MessageKind.ERROR.color else Muted, modifier = Modifier.widthIn(min = 92.dp))
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            avatar?.let { PersonAvatar(it.name, it.avatar, size = 28.dp, zoom = AvatarZoom.HOLD) }
            Text(value, fontSize = 15.sp)
        }
        if (enabled) LuxuryOpenMark(size = 32.dp)
    }
}

// --- board 8: quantity and unit ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuantityEditor(ui: ListsUi, list: ListInfo, lists: ListsController, initial: ItemValues, onCancel: () -> Unit, onSave: (ItemValues, Boolean) -> Unit) {
    val units = ui.data.allUnits
    val remembered = ui.data.product(initial.name)
    var unit by remember { mutableStateOf(initial.unit ?: remembered?.unit ?: "szt.") }
    var quantity by remember { mutableStateOf(initial.quantity ?: 1.0) }
    var pack by remember { mutableStateOf(initial.packSize ?: remembered?.takeIf { it.unit == unit }?.packSize ?: "") }
    var forms by remember { mutableStateOf<ListUnit?>(null) }
    var keep by remember { mutableStateOf(remembered == null || remembered.unit != (initial.unit ?: remembered.unit)) }
    val current = units.firstOrNull { it.name == unit } ?: ListUnit(unit)
    val step = current.step
    val values = initial.copy(quantity = quantity, unit = unit, packSize = pack.takeIf { current.packUnit != null && it.isNotBlank() })

    // The number can be typed too (half a loaf: 0,5), not only stepped (Michał, 25.11).
    var typed by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(Lists.number(quantity))) }
    val setQuantity = { q: Double -> quantity = q; typed = androidx.compose.ui.text.input.TextFieldValue(Lists.number(q)) }
    // A tap puts the cursor after the number, so deleting it is easy (Michał, 25.11): after the tap has set it.
    var focused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(60)
            typed = typed.copy(selection = androidx.compose.ui.text.TextRange(typed.text.length))
        }
    }
    val least = step.coerceAtMost(1.0)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        StepCircle(AppIcons.Minus, "Mniej") {
            val next = Lists.round(quantity - step)
            setQuantity(if (next >= least) next else minOf(quantity, least))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 96.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = typed,
                onValueChange = { value ->
                    val clean = value.text.filter { it.isDigit() || it == ',' || it == '.' }.take(7)
                    typed = value.copy(text = clean, selection = androidx.compose.ui.text.TextRange(value.selection.start.coerceAtMost(clean.length), value.selection.end.coerceAtMost(clean.length)))
                    clean.replace(',', '.').toDoubleOrNull()?.let { if (it > 0) quantity = Lists.round(it) }
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Medium, color = Luxury.Text, textAlign = TextAlign.Center),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Luxury.GoldText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Ilość — stuknij, żeby wpisać" },
            )
            Text(Lists.quantityText(values, units).substringAfter(' ', ""), fontSize = 14.sp, color = Luxury.GoldText, textAlign = TextAlign.Center)
        }
        StepCircle(AppIcons.Plus, "Więcej") { setQuantity(Lists.round(quantity + step)) }
    }
    Text("Stuknij liczbę, żeby wpisać dowolną, np. 0,5.", fontSize = 12.sp, color = Faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    LuxurySectionTitle("Jednostka")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        units.forEach { option ->
            val own = Lists.BUILT_IN_UNITS.none { it.name == option.name }
            Chip(
                option.name, option.name == unit,
                onLongClickLabel = if (own) "Popraw odmianę" else null,
                onLongClick = if (own) ({ forms = option }) else null,
            ) {
                unit = option.name
                if (option.name == "dag" && quantity < 10) setQuantity(30.0)
                pack = ui.data.product(initial.name)?.takeIf { it.unit == option.name }?.packSize ?: ""
            }
        }
        Chip("+ własna", false) { forms = ListUnit("", emptyList()) }
    }
    if (units.any { u -> Lists.BUILT_IN_UNITS.none { it.name == u.name } }) {
        Text("Przytrzymaj własną jednostkę, żeby poprawić jej odmianę.", fontSize = 11.sp, color = Faint)
    }
    forms?.let { editing ->
        UnitFormsDialog(list, initial.name, editing, onDismiss = { forms = null }) { saved ->
            lists.saveUnit(list.id, saved)
            unit = saved.name
            pack = ""
            forms = null
        }
    }
    current.packUnit?.let { packUnit ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${current.forms.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: unit} po", fontSize = 14.sp, color = Muted)
            OutlinedTextField(
                value = pack, onValueChange = { pack = it.take(8) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.widthIn(max = 96.dp),
            )
            Text("$packUnit (opcjonalnie)", fontSize = 13.sp, color = Faint)
        }
    }
    if (initial.name.isNotBlank()) {
        SwitchRow(
            label = "Zapamiętaj dla „${initial.name}”",
            checked = keep,
            enabled = true,
            below = { Text("dla całej społeczności, na każdej liście zakupów", fontSize = 12.sp, color = Muted) },
        ) { keep = it }
    }
    Text("Szybkie dopisywanie też rozumie jednostki: „ziemniaki 2 siatki”, „szynka 20 dag”.", fontSize = 12.sp, color = Muted)
    PopupButtons {
        FramedButton("Bez ilości", gold = false, modifier = Modifier.fillMaxWidth()) {
            onSave(initial.copy(quantity = null, unit = null, packSize = null), false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onCancel)
            Box(modifier = Modifier.weight(1f)) { GoldButton("Zapisz") { onSave(values, keep && initial.name.isNotBlank()) } }
        }
    }
}

@Composable
internal fun StepCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).border(1.dp, Luxury.GoldLink, CircleShape).background(Luxury.GoldBase.copy(alpha = 0.08f))
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Luxury.GoldText) }
}

// --- premium: reminders ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditor(ui: ListsUi, initial: List<ListReminder>, onCancel: () -> Unit, onSave: (List<ListReminder>) -> Unit) {
    val first = initial.firstOrNull()
    var who by remember { mutableStateOf(initial.map { it.memberId }.toSet().ifEmpty { setOf(ui.me.id) }) }
    var days by remember { mutableStateOf(first?.daysBefore ?: 1) }
    var at by remember { mutableStateOf(first?.time ?: "09:00") }
    var kind by remember { mutableStateOf(first?.kind ?: ReminderKind.NOTIFICATION) }
    var pickTime by remember { mutableStateOf(false) }
    LuxurySectionTitle("Kiedy")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0 to "w dniu terminu", 1 to "dzień przed", 3 to "3 dni przed", 7 to "tydzień przed").forEach { (value, label) ->
            Chip(label, days == value) { days = value }
        }
    }
    FieldRow("Godzina", at) { pickTime = true }
    LuxurySectionTitle("Jak")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Powiadomienie", kind == ReminderKind.NOTIFICATION) { kind = ReminderKind.NOTIFICATION }
        Chip("Budzik", kind == ReminderKind.ALARM) { kind = ReminderKind.ALARM }
    }
    LuxurySectionTitle("Komu")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ui.members.forEach { member ->
            PersonChip(if (member.id == ui.me.id) "${member.name} (Ty)" else member.name, member.avatar, member.id in who) {
                who = if (member.id in who) who - member.id else who + member.id
            }
        }
    }
    Text("Każda osoba dostanie przypomnienie na swoim telefonie.", fontSize = 12.sp, color = Muted)
    PopupButtons {
        if (initial.isNotEmpty()) FramedButton("Bez przypomnienia", gold = false, modifier = Modifier.fillMaxWidth()) { onSave(emptyList()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FramedButton("Anuluj", gold = false, modifier = Modifier.weight(1f), onClick = onCancel)
            Box(modifier = Modifier.weight(1f)) { GoldButton("Gotowe") { onSave(who.map { ListReminder(it, kind, days, at) }) } }
        }
    }
    if (pickTime) TimeDialog("Godzina przypomnienia", LocalTime.parse(at), onDismiss = { pickTime = false }) { at = "%02d:%02d".format(it.hour, it.minute) }
}

private fun reminderWords(ui: ListsUi, reminders: List<ListReminder>): String {
    val first = reminders.first()
    val whenText = when (first.daysBefore) {
        0 -> "w dniu"
        1 -> "dzień przed"
        7 -> "tydzień przed"
        else -> "${first.daysBefore} dni przed"
    }
    val who = reminders.joinToString(", ") { if (it.memberId == ui.me.id) "Ty" else ui.member(it.memberId)?.name ?: "?" }
    return "$whenText, ${first.time} · $who"
}

// --- board 9: a conflict, the list's creator decides ---

@Composable
private fun ConflictDialog(
    ui: ListsUi,
    list: ListInfo,
    item: ListItem,
    conflict: ListConflict,
    onDismiss: () -> Unit,
    onDecide: (ConflictDecision) -> Unit,
    onRemove: () -> Unit,
) {
    val escalated = System.currentTimeMillis() - conflict.createdAt > Lists.CONFLICT_ESCALATION_MS
    val decides = list.createdBy == ui.me.id || (escalated && ui.me.permissions[pl.ambif.ambify.shared.Action.LISTS_DELETE_OTHERS] == Mode.ALLOWED)
    var other by remember { mutableStateOf<String?>(null) }
    LuxuryPopup(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LuxurySectionTitle("Konflikt na liście", color = MessageKind.WARNING.color)
            Text("${item.values.name} — ${conflict.field.label}", fontSize = 20.sp, fontFamily = pl.ambif.ambify.ui.theme.Luxury.HeadingFont, fontWeight = FontWeight.Medium)
            Text(
                "Dwie osoby zmieniły ${conflict.field.label} w tym samym czasie. Do decyzji na liście widać wersję zapisaną później.",
                fontSize = 12.sp, color = Muted, lineHeight = 17.sp,
            )
        }
        val typed = other
        if (typed == null) {
            conflict.versions.forEachIndexed { index, version ->
                VersionCard(ui, conflict.field, version, if (decides) ({ onDecide(ConflictDecision(keep = index)) }) else null)
            }
            if (decides) FramedButton("Wprowadź inne", gold = false, modifier = Modifier.fillMaxWidth()) { other = "" }
            else MessageBar(MessageKind.INFO, "Decyduje twórca listy", "${ui.name(list.createdBy)} wybierze wersję. Po 48 h może to zrobić Owner lub Admin.")
        } else {
            val textField = conflict.field in setOf(ItemField.NAME, ItemField.QUANTITY, ItemField.NOTE, ItemField.VARIANT)
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Outline, RoundedCornerShape(16.dp)).background(Luxury.Surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (textField) {
                    OutlinedTextField(
                        value = typed, onValueChange = { other = it }, singleLine = conflict.field != ItemField.NOTE,
                        label = { Text("Nowa ${conflict.field.label} (opcjonalnie)") },
                        placeholder = { Text(if (conflict.field == ItemField.QUANTITY) "np. 1 l — albo zostaw puste" else "albo zostaw puste") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Obie wersje znikną. " + (if (textField) "Puste pole = pozycja zostaje bez tej wartości. " else "Pole zostanie puste. ") +
                        "Jeśli to już nieaktualne, możesz po prostu usunąć pozycję.",
                    fontSize = 12.sp, color = Muted, lineHeight = 17.sp,
                )
            }
            PopupButtons {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FramedButton("Wstecz", gold = false, modifier = Modifier.weight(1f)) { other = null }
                    Box(modifier = Modifier.weight(1f)) {
                        GoldButton("Zapisz") { onDecide(ConflictDecision(other = otherValues(conflict.field, typed, item.values, ui))) }
                    }
                }
            }
            DangerButton("Usuń pozycję z listy", onRemove)
        }
        Text("Bez decyzji przez 48 h rozstrzyga Owner lub Admin.", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        PopupButtons {
            FramedButton("Później", gold = false, modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun VersionCard(ui: ListsUi, field: ItemField, version: ConflictVersion, onKeep: (() -> Unit)?) {
    val member = ui.member(version.userId)
    val late = version.receivedAt - version.madeAt > 2 * 60_000
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Outline, RoundedCornerShape(16.dp)).background(Luxury.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonAvatar(version.userName, member?.avatar, size = 30.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(if (version.userId == ui.me.id) "${version.userName} (Ty)" else version.userName, fontSize = 14.sp)
                Text(time(version.madeAt) + if (late) " · bez sieci, wysłane ${time(version.receivedAt).substringAfterLast(' ')}" else "", fontSize = 12.sp, color = Muted)
            }
            Text(fieldValue(ui, field, version.values).ifEmpty { "—" }, fontSize = if (field == ItemField.NOTE) 14.sp else 22.sp, fontWeight = FontWeight.Medium, maxLines = 3)
        }
        if (onKeep != null) FramedButton("Zostaw tę", modifier = Modifier.fillMaxWidth(), onClick = onKeep)
    }
}

private fun fieldValue(ui: ListsUi, field: ItemField, values: ItemValues): String = when (field) {
    ItemField.NAME -> values.name
    ItemField.QUANTITY -> Lists.quantityText(values, ui.data.allUnits)
    ItemField.DATE -> if (values.urgent) "PILNE" else values.dueDate?.let { LocalDate.parse(it).let { d -> "${d.dayOfMonth}.${d.monthValue}" } }.orEmpty()
    ItemField.ASSIGNEE -> ui.member(values.assigneeId)?.name.orEmpty()
    ItemField.NOTE -> values.note
    ItemField.REMINDERS -> if (values.reminders.isEmpty()) "" else reminderWords(ui, values.reminders)
    ItemField.VARIANT -> values.variant
    ItemField.LINKS -> values.links.joinToString { Lists.domain(it.url) }
}

/** "Wprowadź inne": what the creator typed (or nothing) as the field's new value. */
private fun otherValues(field: ItemField, typed: String, current: ItemValues, ui: ListsUi): ItemValues {
    val text = typed.trim()
    return when (field) {
        ItemField.NAME -> current.copy(name = text.ifEmpty { current.name })
        ItemField.QUANTITY -> if (text.isEmpty()) current.copy(quantity = null, unit = null, packSize = null) else {
            val parsed = Lists.quickAdd("x $text", ui.data.allUnits)
            current.copy(quantity = parsed.quantity, unit = parsed.unit, packSize = parsed.packSize)
        }
        ItemField.NOTE -> current.copy(note = text)
        ItemField.DATE -> current.copy(dueDate = null, recurrence = Recurrence(), urgent = false)
        ItemField.ASSIGNEE -> current.copy(assigneeId = null)
        ItemField.REMINDERS -> current.copy(reminders = emptyList())
        ItemField.VARIANT -> current.copy(variant = text)
        ItemField.LINKS -> current.copy(links = emptyList())
    }
}
