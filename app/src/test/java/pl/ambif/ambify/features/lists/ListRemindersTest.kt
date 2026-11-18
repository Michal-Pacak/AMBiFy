package pl.ambif.ambify.features.lists

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import pl.ambif.ambify.features.lists.data.ListReminders
import pl.ambif.ambify.features.lists.data.ListsData
import pl.ambif.ambify.shared.ItemValues
import pl.ambif.ambify.shared.ListInfo
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.ListReminder
import pl.ambif.ambify.shared.ListType
import pl.ambif.ambify.shared.ReminderKind

class ListRemindersTest {
    private val today = LocalDate.of(2026, 11, 17)
    private val list = ListInfo("l", ListType.DEADLINES, "Opłaty")

    private fun item(id: String, due: String?, checked: Boolean = false, reminders: List<ListReminder>) =
        ListItem(id, "l", ItemValues(name = id, dueDate = due, reminders = reminders), checked = checked)

    @Test
    fun `only my reminders of open items with a date are planned`() {
        val data = ListsData(
            lists = listOf(list),
            items = listOf(
                item("czynsz", "2026-11-20", reminders = listOf(ListReminder("me", ReminderKind.NOTIFICATION, 1, "09:00"), ListReminder("other", ReminderKind.ALARM, 0))),
                item("prąd", "2026-11-25", checked = true, reminders = listOf(ListReminder("me", ReminderKind.NOTIFICATION, 0))),
                item("bez daty", null, reminders = listOf(ListReminder("me", ReminderKind.NOTIFICATION, 0))),
            ),
        )
        val planned = ListReminders.remindersOf(data, "me", today)
        assertEquals(listOf("czynsz"), planned.map { it.title })
        assertEquals("Opłaty · termin jutro", planned.single().text)
        assertEquals(ReminderKind.NOTIFICATION, planned.single().kind)
    }
}
