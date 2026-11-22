package pl.ambif.ambify.features.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.features.lists.data.ListsData
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListChange
import pl.ambif.ambify.shared.ListChangeKind
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.ListUnit
import pl.ambif.ambify.shared.Lists

class ListsStateTest {
    private val data = ListsData(
        lists = listOf(ListInfo("L", ListType.SHOPPING, "Biedronka")),
        items = listOf(
            ListItem("a", "L", ItemValues("Masło"), checked = true, addedAt = 1),
            ListItem("b", "L", ItemValues("Chleb"), addedAt = 2),
        ),
    )

    private fun change(kind: ListChangeKind, itemId: String? = null, checked: Boolean = false) =
        ListChange("c", kind, "L", itemId, madeAt = 100, checked = checked)

    @Test
    fun finishingMovesDoneThingsToTheHistory() {
        val after = data.applied(change(ListChangeKind.CLEAR_CHECKED))
        assertEquals(listOf("b"), after.itemsOf("L").map { it.id })
        assertEquals(listOf("a"), after.historyOf("L").map { it.id })
        assertEquals(100L, after.historyOf("L").single().clearedAt)
        val back = after.applied(change(ListChangeKind.CHECK_ITEM, "a", checked = false))
        assertTrue(back.historyOf("L").isEmpty())
        assertFalse(back.itemsOf("L").single { it.id == "a" }.checked)
    }

    @Test
    fun uncheckAllKeepsEverythingOnTheList() {
        val after = data.applied(change(ListChangeKind.UNCHECK_ALL))
        assertEquals(2, after.itemsOf("L").size)
        assertTrue(after.itemsOf("L").none { it.checked })
    }

    @Test
    fun aListIsDoneOnlyWhenNothingComesBack() {
        assertFalse(allDone(data.items))
        assertTrue(allDone(data.items.map { it.copy(checked = true) }))
        assertFalse(allDone(emptyList()))
    }

    @Test
    fun addingRemembersOfflineAndForgettingRemoves() {
        val added = data.applied(ListChange("c1", ListChangeKind.ADD_ITEM, "L", "n", madeAt = 100, values = ItemValues("Mleko", 2.0, "l", variant = "3,2%")))
            .applied(ListChange("c2", ListChangeKind.ADD_ITEM, "L", "m", madeAt = 200, values = ItemValues("mleko", 1.0, "l", variant = "3,2%")))
        val entry = added.memoryOf(pl.ambif.ambify.shared.MemoryScope.SHOPPING).single()
        assertEquals("mleko|3,2%", entry.key)
        assertEquals(2, entry.uses)
        assertEquals(1.0, entry.values.quantity)
        val forgotten = added.applied(ListChange("c3", ListChangeKind.FORGET_PRODUCT, "L", madeAt = 300, values = ItemValues("Mleko", variant = "3,2%")))
        assertTrue(forgotten.memory.isEmpty())
    }

    @Test
    fun searchSortAndRestore() {
        val memory = listOf(
            pl.ambif.ambify.shared.ListMemory(pl.ambif.ambify.shared.MemoryScope.SHOPPING, "ser|żółty", ItemValues("Ser", variant = "żółty"), uses = 3, lastUsedAt = 1),
            pl.ambif.ambify.shared.ListMemory(pl.ambif.ambify.shared.MemoryScope.SHOPPING, "chleb|", ItemValues("Chleb"), uses = 9, lastUsedAt = 5),
        )
        assertEquals(listOf("Ser"), memoryShown(memory, "zolty", null, MemorySort.OFTEN).map { it.values.name })
        assertEquals(listOf("Chleb", "Ser"), memoryShown(memory, "", null, MemorySort.OFTEN).map { it.values.name })
        assertEquals(listOf("Chleb", "Ser"), memoryShown(memory, "", null, MemorySort.ALPHA).map { it.values.name })
        assertEquals(listOf("Ser"), memoryShown(memory, "", setOf("ser|żółty"), MemorySort.RECENT).map { it.values.name })
        // Back from "Kupione": the new amount wins, the rest (kind, note) stays.
        val back = restoredValues(ItemValues("Mleko", 2.0, "l", variant = "3,2%", note = "Łaciate"), ItemValues("Mleko", 1.0, "l"))
        assertEquals(ItemValues("Mleko", 1.0, "l", variant = "3,2%", note = "Łaciate"), back)
        assertEquals(2.0, restoredValues(ItemValues("Mleko", 2.0, "l"), ItemValues("Mleko")).quantity)
    }

    @Test
    fun aCopyFromTheOlderAppAsksForEverythingOnce() {
        val old = ListsData(rev = 50)
        assertEquals(0L, old.since)
        val merged = pl.ambif.ambify.features.lists.data.ListsState(old).merged(
            emptyList(),
            pl.ambif.ambify.shared.ListSyncResponse(rev = 52, memory = listOf(pl.ambif.ambify.shared.ListMemory(pl.ambif.ambify.shared.MemoryScope.SHOPPING, "masło|", ItemValues("Masło"), rev = 10))),
            now = 1,
        ).state.base
        assertEquals(52L, merged.since)
        assertEquals(1, merged.memory.size)
    }

    // --- 0.7.2 ---

    @Test
    fun subItemsGoWithTheirItemAlsoOffline() {
        val sub = { id: String, parent: String, name: String, checked: Boolean ->
            ListChange(id, ListChangeKind.ADD_ITEM, "L", id, madeAt = 50, values = ItemValues(name), parentId = parent)
                .let { data.applied(it) }
        }
        var d = sub("s1", "a", "Masło extra", false)
        d = d.applied(ListChange("s2", ListChangeKind.ADD_ITEM, "L", "s2", madeAt = 51, values = ItemValues("Szczoteczka"), parentId = "b"))
        assertEquals(listOf("s1"), d.subItemsOf("a").map { it.id })
        // "a" is done: it goes to the history with its sub-item; "b" is open, its sub-item stays.
        val cleared = d.applied(change(ListChangeKind.CLEAR_CHECKED))
        assertEquals(setOf("a", "s1"), cleared.historyOf("L").map { it.id }.toSet())
        assertEquals(listOf("s2"), cleared.subItemsOf("b").map { it.id })
        // Deleting "b" deletes its sub-item.
        assertEquals(emptyList<String>(), cleared.applied(change(ListChangeKind.DELETE_ITEM, "b")).itemsOf("L").map { it.id })
    }

    @Test
    fun aCorrectedUnitIsUsedAtOnce() {
        val unit = ListUnit("wiaderko", emptyList())
        val guessed = data.copy(units = listOf(unit))
        assertEquals("3 wiaderka", Lists.quantityText(ItemValues("Ogórki", 3.0, "wiaderko"), guessed.allUnits))
        val fixed = guessed.applied(ListChange("u", ListChangeKind.SAVE_UNIT, "L", madeAt = 1, unit = ListUnit("wiaderko", listOf("wiaderko", "wiadra", "wiader"))))
        assertEquals("8 wiader", Lists.quantityText(ItemValues("Ogórki", 8.0, "wiaderko"), fixed.allUnits))
    }
}
