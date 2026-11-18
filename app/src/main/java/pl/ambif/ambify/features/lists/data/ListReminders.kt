package pl.ambif.ambify.features.lists.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import pl.ambif.ambify.modules.timetable.PlannedReminder
import pl.ambif.ambify.modules.timetable.ReminderScheduler

/**
 * Reminders of list items (premium LISTS_REMINDERS): the ones set for me, on items with a date that are not done,
 * planned on this phone like the timetable's (a notification or an alarm), in their own slot.
 */
object ListReminders {
    fun plan(context: Context, data: ListsData, me: String) {
        ReminderScheduler.plan(context, remindersOf(data, me, LocalDate.now()), ReminderScheduler.SLOT_LISTS)
    }

    internal fun remindersOf(data: ListsData, me: String, today: LocalDate): List<PlannedReminder> {
        val lists = data.lists.filter { !it.archived }.associateBy { it.id }
        return data.items.filter { !it.checked && !it.cleared && it.values.dueDate != null && it.listId in lists }.flatMap { item ->
            val due = runCatching { LocalDate.parse(item.values.dueDate) }.getOrNull() ?: return@flatMap emptyList()
            if (due.isBefore(today.minusDays(1))) return@flatMap emptyList()
            item.values.reminders.filter { it.memberId == me }.mapNotNull { reminder ->
                val time = runCatching { LocalTime.parse(reminder.time) }.getOrNull() ?: return@mapNotNull null
                val at = due.minusDays(reminder.daysBefore.toLong()).atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val whenText = when (reminder.daysBefore) {
                    0 -> "dziś"
                    1 -> "jutro"
                    else -> "za ${reminder.daysBefore} dni (${due.dayOfMonth}.${due.monthValue})"
                }
                PlannedReminder(
                    id = ("list:" + item.id + reminder.daysBefore + reminder.time).hashCode(),
                    at = at,
                    kind = reminder.kind,
                    title = item.values.name,
                    text = "${lists[item.listId]?.name.orEmpty()} · termin $whenText",
                )
            }
        }
    }
}
