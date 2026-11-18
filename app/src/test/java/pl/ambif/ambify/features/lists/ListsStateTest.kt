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
}
