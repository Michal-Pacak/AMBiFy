package pl.ambif.ambify.features.lists

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListMemory
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.Lists

/** Board 17, the screen of lists: what "Sortuj" may do there. */
internal enum class ListsSort(val label: String, val description: String) {
    TYPE("Według rodzaju", "domyślnie · rzeczy, zadania, terminy"),
    ALPHA("Alfabetycznie", "A–Ż"),
    CHANGED("Ostatnio zmienione", "najnowsze na górze"),
    DATE("Najbliższy termin", "z pilnymi i najbliższymi na górze"),
    OWNER("Według twórcy", "nagłówki osób"),
}

/** Board 17, inside a list: what "Sortuj" may do there (within each section). */
internal enum class ItemSort(val label: String, val description: String) {
    ADDED("Kolejność dodania", "jak były dopisywane"),
    ALPHA("Alfabetycznie", "A–Ż"),
    PERSON("Według osoby", "dla kogo / kto dodał · nagłówki osób"),
    ROLE("Według roli", "od najwyższej · nagłówki ról"),
    DATE("Według terminu", "pilne, po terminie, najbliższe, bez daty"),
}

/**
 * The person's own way of looking at the lists, kept on this phone only (never sent): sorting of each level,
 * "Zostaw" after a list was done, pairs that are not duplicates. Reading [tick] makes a screen follow the changes.
 */
internal class ListPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lists_view", Context.MODE_PRIVATE)
    private var tick by mutableIntStateOf(0)

    private fun changed() { tick++ }

    fun listsSort(): ListsSort {
        tick
        return prefs.getString("lists_sort", null)?.let { runCatching { ListsSort.valueOf(it) }.getOrNull() } ?: ListsSort.TYPE
    }

    fun setListsSort(sort: ListsSort) { prefs.edit().putString("lists_sort", sort.name).apply(); changed() }

    fun itemSort(list: ListInfo): ItemSort {
        tick
        return prefs.getString("sort_${list.id}", null)?.let { runCatching { ItemSort.valueOf(it) }.getOrNull() }
            ?: if (list.type == ListType.DEADLINES) ItemSort.DATE else ItemSort.ADDED
    }

    fun setItemSort(list: ListInfo, sort: ItemSort) { prefs.edit().putString("sort_${list.id}", sort.name).apply(); changed() }

    /** Board 17, "Podlisty sortuj tak samo" (on by default): sub-lists without their own choice follow the list. */
    fun subsSameSort(listId: String): Boolean { tick; return prefs.getBoolean("subs_same_$listId", true) }

    fun setSubsSameSort(listId: String, same: Boolean) { prefs.edit().putBoolean("subs_same_$listId", same).apply(); changed() }

    /** A sub-list's own sorting; null = "Jak lista". */
    fun ownSubSort(itemId: String): ItemSort? {
        tick
        return prefs.getString("sub_sort_$itemId", null)?.let { runCatching { ItemSort.valueOf(it) }.getOrNull() }
    }

    fun setOwnSubSort(itemId: String, sort: ItemSort?) {
        prefs.edit().apply { if (sort == null) remove("sub_sort_$itemId") else putString("sub_sort_$itemId", sort.name) }.apply()
        changed()
    }

    /** The order a sub-list is shown in: its own, else the list's (when inherited), else as added. */
    fun subSort(list: ListInfo, parent: ListItem): ItemSort =
        ownSubSort(parent.id) ?: if (subsSameSort(list.id)) itemSort(list) else ItemSort.ADDED

    /** "Zostaw" after the list was done: not asked again until something newer than [addedAt] is added. */
    fun doneAnswered(listId: String): Long = prefs.getLong("done_$listId", 0)

    fun answerDone(listId: String, addedAt: Long) { prefs.edit().putLong("done_$listId", addedAt).apply(); changed() }

    /** "Nie pytaj więcej o tę listę" (0.7.1): a list used without end never asks "co z listą?". */
    fun neverAskDone(listId: String): Boolean { tick; return prefs.getBoolean("never_done_$listId", false) }

    fun setNeverAskDone(listId: String) { prefs.edit().putBoolean("never_done_$listId", true).apply(); changed() }

    /** The done section ("Kupione · 5") folded or not, per list. */
    fun doneOpen(listId: String): Boolean { tick; return prefs.getBoolean("done_open_$listId", true) }

    fun setDoneOpen(listId: String, open: Boolean) { prefs.edit().putBoolean("done_open_$listId", open).apply(); changed() }

    /** A kind of lists ("Listy rzeczy") opened in the widget "Aktywne listy" [widgetId]; folded at first. */
    /** Board 16: a sub-list open under its item (kept after the app is closed). */
    fun subOpen(itemId: String): Boolean {
        tick
        if (takeOpened(itemId)) prefs.edit().putBoolean("sub_open_$itemId", true).apply()
        return prefs.getBoolean("sub_open_$itemId", false)
    }

    fun setSubOpen(itemId: String, open: Boolean) { prefs.edit().putBoolean("sub_open_$itemId", open).apply(); changed() }

    /** Board 27: the items marked on the open list (not kept); none = no marking. */
    val marked = androidx.compose.runtime.mutableStateListOf<String>()

    /** The group just made with "Zrób z tego grupę": its field gets the keyboard (not kept). */
    var focusSub by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun widgetKindOpen(widgetId: String, type: ListType): Boolean { tick; return prefs.getBoolean("widget_kind_${widgetId}_${type.name}", false) }

    fun setWidgetKindOpen(widgetId: String, type: ListType, open: Boolean) { prefs.edit().putBoolean("widget_kind_${widgetId}_${type.name}", open).apply(); changed() }

    /** "To różne rzeczy — zostaw obie": no "Podwójnie?" for this pair any more. */
    fun notDuplicates(a: String, b: String): Boolean {
        tick
        return pairKey(a, b) in prefs.getStringSet("different", emptySet()).orEmpty()
    }

    fun markDifferent(a: String, b: String) {
        val set = prefs.getStringSet("different", emptySet()).orEmpty() + pairKey(a, b)
        prefs.edit().putStringSet("different", set.toSet()).apply()
        changed()
    }

    private fun pairKey(a: String, b: String) = listOf(a, b).sorted().joinToString("|")

    companion object {
        private val opened = mutableSetOf<String>()

        /** A group made by "Dodaj do grupy" shows open on the list (the prefs are per screen, this is per app). */
        fun openNow(itemId: String) { synchronized(opened) { opened += itemId } }

        fun takeOpened(itemId: String): Boolean = synchronized(opened) { opened.remove(itemId) }
    }
}

/** A group of items under a header ("BARTEK · 2"); [title] null = no header. */
internal data class ItemGroup(val title: String?, val items: List<ListItem>)

/** [items] in the chosen order; by person or role they come in groups with headers, people without one last. */
internal fun sorted(ui: ListsUi, list: ListInfo, items: List<ListItem>, sort: ItemSort): List<ItemGroup> {
    fun person(item: ListItem): String? = if (list.type == ListType.TASKS) item.values.assigneeId else item.addedBy
    return when (sort) {
        ItemSort.ADDED -> listOf(ItemGroup(null, items.sortedBy { it.addedAt }))
        ItemSort.ALPHA -> listOf(ItemGroup(null, items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.values.name })))
        ItemSort.DATE -> listOf(ItemGroup(null, items.sortedWith(dateOrder())))
        ItemSort.PERSON -> items.groupBy { person(it) }.entries
            .sortedWith(compareBy({ it.key == null }, { ui.name(it.key).lowercase() }))
            .map { (id, group) -> ItemGroup(if (id == null) "Bez osoby" else ui.name(id), group.sortedBy { it.addedAt }) }
        ItemSort.ROLE -> items.groupBy { ui.member(person(it))?.role }.entries
            .sortedWith(compareBy({ it.key == null }, { -(it.key?.rank ?: 0) }))
            .map { (role, group) -> ItemGroup(role?.name ?: "Bez osoby", group.sortedBy { it.addedAt }) }
    }
}

/** Urgent first, then by date (late ones come first by themselves), without a date last. */
internal fun dateOrder(): Comparator<ListItem> =
    compareBy<ListItem>({ !it.values.urgent }, { it.values.dueDate == null }, { it.values.dueDate ?: "" }, { it.addedAt })

/** The lists of the screen in the chosen order, in groups: by kind (the sections) or by creator. */
internal fun sortedLists(ui: ListsUi, lists: List<ListInfo>, sort: ListsSort): List<Pair<String?, List<ListInfo>>> {
    fun changed(list: ListInfo): Long =
        maxOf(list.rev, ui.data.items.filter { it.listId == list.id }.maxOfOrNull { it.rev } ?: 0)
    fun nearest(list: ListInfo): String {
        val open = ui.data.itemsOf(list.id).filter { !it.checked }
        if (open.any { it.values.urgent }) return "0000"
        return open.mapNotNull { it.values.dueDate }.minOrNull() ?: "9999"
    }
    return when (sort) {
        ListsSort.TYPE -> ListType.entries.mapNotNull { type ->
            lists.filter { it.type == type }.takeIf { it.isNotEmpty() }?.let { typeLabel(type) to it }
        }
        ListsSort.ALPHA -> listOf(null to lists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        ListsSort.CHANGED -> listOf(null to lists.sortedByDescending { changed(it) })
        ListsSort.DATE -> listOf(null to lists.sortedWith(compareBy({ nearest(it) }, { it.name.lowercase() })))
        ListsSort.OWNER -> lists.groupBy { it.createdBy }.entries
            .sortedBy { if (it.key == ui.me.id) "" else ui.name(it.key).lowercase() }
            .map { (id, group) -> ui.name(id) to group.sortedBy { it.name.lowercase() } }
    }
}

internal fun typeLabel(type: ListType) = when (type) {
    ListType.SHOPPING -> "Listy rzeczy"
    ListType.TASKS -> "Zadania"
    ListType.DEADLINES -> "Terminy"
}

/** All open items are done (and none of them comes back by itself, like a recurring task). */
internal fun allDone(items: List<ListItem>): Boolean =
    items.isNotEmpty() && items.all { it.checked } &&
        items.none { it.values.recurrence.frequency != Frequency.NONE && it.values.dueDate != null }

/** Two open items that are the same thing ("Podwójnie?"), not marked as different. */
internal fun duplicateOf(prefs: ListPrefs, items: List<ListItem>, item: ListItem): ListItem? =
    items.firstOrNull { other ->
        other.id != item.id && !other.checked && !item.checked && Lists.sameThing(other.values, item.values) &&
            !prefs.notDuplicates(other.id, item.id)
    }

internal fun isLate(item: ListItem, today: LocalDate = LocalDate.now()): Boolean =
    !item.values.urgent && item.values.dueDate?.let { LocalDate.parse(it).isBefore(today) } == true

/**
 * [existing] (done, e.g. in "Kupione") back on the list with what was typed now: a new amount, date or person
 * replaces the old one, what was not typed stays (board 20).
 */
internal fun restoredValues(existing: ItemValues, wanted: ItemValues): ItemValues {
    var values = existing
    if (wanted.quantity != null) values = values.copy(quantity = wanted.quantity, unit = wanted.unit, packSize = wanted.packSize)
    if (wanted.dueDate != null || wanted.urgent) values = values.copy(dueDate = wanted.dueDate, urgent = wanted.urgent, recurrence = wanted.recurrence)
    if (wanted.assigneeId != null) values = values.copy(assigneeId = wanted.assigneeId)
    if (wanted.note.isNotBlank()) values = values.copy(note = wanted.note)
    return values
}

/** "Najczęściej", "Ostatnio", "A–Ż" in "Z pamięci" (board 21). */
internal enum class MemorySort(val label: String) { OFTEN("Najczęściej"), RECENT("Ostatnio"), ALPHA("A–Ż") }

/** The memory as "Z pamięci" shows it: matching [query] (also without Polish letters), [onlyKeys] if given, sorted. */
internal fun memoryShown(memory: List<ListMemory>, query: String, onlyKeys: Set<String>?, sort: MemorySort): List<ListMemory> {
    val q = Lists.searchKey(query.trim())
    return memory
        .filter { onlyKeys == null || it.key in onlyKeys }
        .filter { q.isEmpty() || Lists.searchKey("${it.values.name} ${it.values.variant}").contains(q) }
        .sortedWith(
            when (sort) {
                MemorySort.OFTEN -> compareByDescending<ListMemory> { it.uses }.thenByDescending { it.lastUsedAt }
                MemorySort.RECENT -> compareByDescending { it.lastUsedAt }
                MemorySort.ALPHA -> compareBy(String.CASE_INSENSITIVE_ORDER) { "${it.values.name} ${it.values.variant}" }
            },
        )
}
