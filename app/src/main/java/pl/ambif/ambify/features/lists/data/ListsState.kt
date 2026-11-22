package pl.ambif.ambify.features.lists.data

import kotlinx.serialization.Serializable
import pl.ambif.ambify.shared.ChangeResult
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListConflict
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListMemory
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Lists
import pl.ambif.ambify.shared.MemoryScope
import pl.ambif.ambify.shared.ProductUnit
import pl.ambif.ambify.shared.ProductVariant

/**
 * The phone's copy of one community's lists: [base] as the server last told it, and the person's [pending] changes
 * not confirmed yet (made offline, or still on the way). What the screens show is [view]: the base with the waiting
 * changes put on top, so a change is visible at once and stays visible until the server has it.
 */
@Serializable
data class ListsState(
    val base: ListsData = ListsData(),
    val pending: List<ListChange> = emptyList(),
    /** When the server last answered (epoch millis); null = never. */
    val syncedAt: Long? = null,
) {
    val view: ListsData get() = pending.fold(base) { data, change -> data.applied(change) }

    fun withChange(change: ListChange): ListsState = copy(pending = pending + change)

    /**
     * The server's answer to [sent] (the first changes of [pending], sent with [ListSyncRequest.since] = base.rev):
     * answered changes leave the queue, the server's data replaces the base. Refused changes come back in
     * [Merge.refused] with the reason to show.
     */
    fun merged(sent: List<ListChange>, response: ListSyncResponse, now: Long): Merge {
        val answered = response.results.associateBy { it.id }
        val done = sent.filter { it.id in answered }.map { it.id }.toSet()
        val restarted = response.rev < base.rev
        val start = if (restarted) ListsData() else base
        val visible = response.listIds.toSet()
        val lists = (start.lists.associateBy { it.id } + response.lists.associateBy { it.id })
            .values.filter { !it.deleted && it.id in visible }
        val listIds = lists.map { it.id }.toSet()
        val items = (start.items.associateBy { it.id } + response.items.associateBy { it.id })
            .values.filter { !it.deleted && it.listId in listIds }
        val conflicts = (start.conflicts.associateBy { it.id } + response.conflicts.associateBy { it.id })
            .values.filter { it.listId in listIds }
        val data = ListsData(
            rev = response.rev,
            lists = lists.sortedBy { it.createdAt },
            items = items.sortedBy { it.addedAt },
            conflicts = conflicts.sortedBy { it.createdAt },
            units = (start.units.associateBy { it.name } + response.units.associateBy { it.name }).values.toList(),
            products = (start.products.associateBy { it.product } + response.products.associateBy { it.product }).values.toList(),
            variants = (start.variants.associateBy { it.product to it.variant } + response.variants.associateBy { it.product to it.variant })
                .values.sortedByDescending { it.rev },
            memory = (start.memory.associateBy { it.scope to it.key } + response.memory.associateBy { it.scope to it.key })
                .values.filter { !it.deleted },
            version = ListsData.VERSION,
        )
        return Merge(
            state = ListsState(data, pending.filterNot { it.id in done }, now),
            refused = response.results.filter { !it.ok },
            queued = response.results.filter { it.ok && it.queued },
            newConflicts = response.conflicts.filter { c -> !c.resolved && start.conflicts.none { it.id == c.id } },
            resolved = response.conflicts.filter { c -> c.resolved && start.conflicts.any { it.id == c.id && !it.resolved } },
        )
    }

    data class Merge(
        val state: ListsState,
        val refused: List<ChangeResult>,
        val queued: List<ChangeResult>,
        val newConflicts: List<ListConflict>,
        val resolved: List<ListConflict>,
    )
}

@Serializable
data class ListsData(
    val rev: Long = 0,
    val lists: List<ListInfo> = emptyList(),
    val items: List<ListItem> = emptyList(),
    val conflicts: List<ListConflict> = emptyList(),
    val units: List<ListUnit> = emptyList(),
    val products: List<ProductUnit> = emptyList(),
    val variants: List<ProductVariant> = emptyList(),
    /** The community's memory of things added before (board 21), without forgotten ones. */
    val memory: List<ListMemory> = emptyList(),
    /** Which app made this copy; an older one (e.g. 0.7.0 ignored the memory) asks the server for everything once. */
    val version: Int = 0,
) {
    companion object {
        /**
         * 1 = 0.7.1, with the memory; 2 = 0.7.2, sub-items, access of items and guessed forms of units. Raise it when
         * the phone must take everything again after an update.
         */
        const val VERSION = 2
    }

    /** What the next sync asks for: everything newer than the copy, or everything when the copy is from an older app. */
    val since: Long get() = if (version < VERSION) 0 else rev

    /** Built-in units and the community's own. */
    val allUnits: List<ListUnit> get() = Lists.BUILT_IN_UNITS + units.filter { own -> Lists.BUILT_IN_UNITS.none { it.name == own.name } }

    fun itemsOf(listId: String): List<ListItem> = items.filter { it.listId == listId && !it.cleared }

    /** The sub-items of [itemId] (premium "Podlisty"), in the order they were added. */
    fun subItemsOf(itemId: String): List<ListItem> = items.filter { it.parentId == itemId && !it.cleared }

    fun item(id: String?): ListItem? = items.firstOrNull { it.id == id }

    /** Done things moved to the history ("Zakończ zakupy"), newest first; they stay until the list is deleted. */
    fun historyOf(listId: String): List<ListItem> = items.filter { it.listId == listId && it.cleared }.sortedByDescending { it.clearedAt ?: 0 }

    /** Kinds of [name] used before in the community, newest first ("3,2%", "2%"). */
    fun variantsOf(name: String): List<String> = Lists.productKey(name).let { key -> variants.filter { it.product == key }.map { it.variant } }

    fun memoryOf(scope: MemoryScope): List<ListMemory> = memory.filter { it.scope == scope }

    fun openConflicts(itemId: String): List<ListConflict> = conflicts.filter { it.itemId == itemId && !it.resolved }

    fun product(name: String): ProductUnit? = products.firstOrNull { it.product == Lists.productKey(name) }

    /**
     * [change] as the phone shows it before the server answers. "Who" fields are filled in by the server; here the
     * change's [ListChange.madeAt] stands in for the times. [me] is not needed: the item keeps its old author.
     */
    fun applied(change: ListChange): ListsData = when (change.kind) {
        ListChangeKind.CREATE_LIST -> change.list?.let { list ->
            copy(lists = lists.filterNot { it.id == change.listId } + list.copy(id = change.listId, createdAt = change.madeAt))
        } ?: this
        ListChangeKind.UPDATE_LIST -> change.list?.let { list ->
            copy(lists = lists.map {
                if (it.id == change.listId) {
                    it.copy(name = list.name, everyone = list.everyone, memberIds = list.memberIds, archived = list.archived, style = list.style, access = list.access)
                } else it
            })
        } ?: this
        ListChangeKind.DELETE_LIST -> copy(lists = lists.filterNot { it.id == change.listId }, items = items.filterNot { it.listId == change.listId })
        ListChangeKind.ADD_ITEM -> {
            val values = change.values
            val itemId = change.itemId
            if (values == null || itemId == null || items.any { it.id == itemId }) this
            else copy(items = items + ListItem(itemId, change.listId, values, addedAt = change.madeAt, parentId = change.parentId))
                .remembered(change.listId, values, change.madeAt, added = true)
        }
        ListChangeKind.EDIT_ITEM -> mapItem(change.itemId) { item ->
            val values = change.values ?: return@mapItem item
            item.copy(values = change.fields.fold(item.values) { acc, field -> field.copy(values, acc) }, changedAt = change.madeAt)
        }.let { data -> data.items.firstOrNull { it.id == change.itemId }?.let { data.remembered(it.listId, it.values, change.madeAt, added = false) } ?: data }
        ListChangeKind.FORGET_PRODUCT -> {
            val scope = lists.firstOrNull { it.id == change.listId }?.let { Lists.scopeOf(it) }
            val key = change.values?.let { Lists.memoryKey(it) }
            copy(memory = memory.filterNot { it.scope == scope && it.key == key })
        }
        ListChangeKind.CHECK_ITEM -> mapItem(change.itemId) {
            if (change.checked) it.copy(checked = true, checkedAt = change.madeAt)
            else it.copy(checked = false, checkedAt = null, checkedBy = null, cleared = false, clearedAt = null, clearedBy = null)
        }
        ListChangeKind.DELETE_ITEM -> copy(items = items.filterNot { it.id == change.itemId || (it.parentId != null && it.parentId == change.itemId) })
        ListChangeKind.CLEAR_CHECKED -> {
            // Like the server: done items with their sub-items; a done sub-item of an open item stays under it.
            val done = items.filter { it.listId == change.listId && it.checked && !it.cleared && it.parentId == null }.map { it.id }.toSet()
            copy(items = items.map { if (it.id in done || it.parentId in done) it.copy(cleared = true, clearedAt = change.madeAt) else it })
        }
        ListChangeKind.SAVE_UNIT -> change.unit?.let { unit ->
            copy(units = units.filterNot { it.name == unit.name } + unit)
        } ?: this
        ListChangeKind.ITEM_ACCESS -> mapItem(change.itemId) { it.copy(access = change.access) }
        ListChangeKind.UNCHECK_ALL -> copy(items = items.map {
            if (it.listId == change.listId && it.checked && !it.cleared) it.copy(checked = false, checkedAt = null, checkedBy = null) else it
        })
    }

    /** The memory as the server will have it (also offline): added = counted once more. */
    private fun remembered(listId: String, values: ItemValues, at: Long, added: Boolean): ListsData {
        val list = lists.firstOrNull { it.id == listId } ?: return this
        val scope = Lists.scopeOf(list)
        val key = Lists.memoryKey(values)
        val old = memory.firstOrNull { it.scope == scope && it.key == key }
        if (old == null && !added) return this
        val entry = (old ?: ListMemory(scope, key, values, uses = 0)).let {
            it.copy(values = Lists.memoryValues(values), uses = it.uses + if (added) 1 else 0, lastUsedAt = if (added) at else it.lastUsedAt)
        }
        return copy(memory = memory.filterNot { it.scope == scope && it.key == key } + entry)
    }

    private fun mapItem(id: String?, block: (ListItem) -> ListItem) = copy(items = items.map { if (it.id == id) block(it) else it })
}

/** The fields of an item that differ between [before] and [after] (what an edit sends). */
fun changedFields(before: ItemValues, after: ItemValues): List<ItemField> =
    ItemField.entries.filterNot { it.same(after, before) }
