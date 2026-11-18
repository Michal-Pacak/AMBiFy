package pl.ambif.ambify.features.lists.data

import kotlinx.serialization.Serializable
import pl.ambif.ambify.shared.ChangeResult
import pl.ambif.ambify.shared.ItemField
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListConflict
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListSyncResponse
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Lists
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
) {
    /** Built-in units and the community's own. */
    val allUnits: List<ListUnit> get() = Lists.BUILT_IN_UNITS + units.filter { own -> Lists.BUILT_IN_UNITS.none { it.name == own.name } }

    fun itemsOf(listId: String): List<ListItem> = items.filter { it.listId == listId && !it.cleared }

    /** Done things moved to the history ("Zakończ zakupy"), newest first; they stay until the list is deleted. */
    fun historyOf(listId: String): List<ListItem> = items.filter { it.listId == listId && it.cleared }.sortedByDescending { it.clearedAt ?: 0 }

    /** Kinds of [name] used before in the community, newest first ("3,2%", "2%"). */
    fun variantsOf(name: String): List<String> = Lists.productKey(name).let { key -> variants.filter { it.product == key }.map { it.variant } }

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
            else copy(items = items + ListItem(itemId, change.listId, values, addedAt = change.madeAt))
        }
        ListChangeKind.EDIT_ITEM -> mapItem(change.itemId) { item ->
            val values = change.values ?: return@mapItem item
            item.copy(values = change.fields.fold(item.values) { acc, field -> field.copy(values, acc) }, changedAt = change.madeAt)
        }
        ListChangeKind.CHECK_ITEM -> mapItem(change.itemId) {
            if (change.checked) it.copy(checked = true, checkedAt = change.madeAt)
            else it.copy(checked = false, checkedAt = null, checkedBy = null, cleared = false, clearedAt = null, clearedBy = null)
        }
        ListChangeKind.DELETE_ITEM -> copy(items = items.filterNot { it.id == change.itemId })
        ListChangeKind.CLEAR_CHECKED -> copy(items = items.map {
            if (it.listId == change.listId && it.checked && !it.cleared) it.copy(cleared = true, clearedAt = change.madeAt) else it
        })
        ListChangeKind.UNCHECK_ALL -> copy(items = items.map {
            if (it.listId == change.listId && it.checked && !it.cleared) it.copy(checked = false, checkedAt = null, checkedBy = null) else it
        })
    }

    private fun mapItem(id: String?, block: (ListItem) -> ListItem) = copy(items = items.map { if (it.id == id) block(it) else it })
}

/** The fields of an item that differ between [before] and [after] (what an edit sends). */
fun changedFields(before: pl.ambif.ambify.shared.ItemValues, after: pl.ambif.ambify.shared.ItemValues): List<ItemField> =
    ItemField.entries.filterNot { it.same(after, before) }
