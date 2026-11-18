package pl.ambif.ambify.modules.timetable

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.TimetableWeek

class TimetableCacheTest {
    private val week = TimetableWeek(
        monday = "2026-11-16",
        students = emptyList(),
        lessons = mapOf("s1" to listOf(Lesson("2026-11-16", 1, "Matematyka", "21", "[nauczyciel]", "08:00-08:45"))),
        activities = emptyList(),
        occurrences = emptyList(),
        diaryConnected = true,
    )

    /** Up to v0.6.10 the app had no serialization plugin: the copy was never written, offline showed nothing. */
    @Test
    fun savedWeekAndPlannedRemindersSurviveJson() {
        val saved = SavedWeek(123, week)
        assertEquals(saved, Json.decodeFromString<SavedWeek>(Json.encodeToString(saved)))
        val planned = listOf(PlannedReminder(1, 0L, ReminderKind.ALARM, "Basen", "16:00"))
        assertEquals(planned, Json.decodeFromString<List<PlannedReminder>>(Json.encodeToString(planned)))
    }

    @Test
    fun copyWithLessonsShowsThePlanEvenWithoutTheFlag() {
        val old = SavedWeek(1, week.copy(diaryConnected = false))
        assertTrue(TimetableCache.withLessonsConnected(old).week.diaryConnected)
    }
}
