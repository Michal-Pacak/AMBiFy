package pl.ambif.ambify.features.timetable

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.ambif.ambify.modules.timetable.ReminderScheduler
import pl.ambif.ambify.modules.timetable.TimetableCache
import pl.ambif.ambify.shared.ExtraActivity
import pl.ambif.ambify.shared.Frequency
import pl.ambif.ambify.shared.MonthMode
import pl.ambif.ambify.shared.Occurrence
import pl.ambif.ambify.shared.Recurrence
import pl.ambif.ambify.shared.Reminder
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.Student
import pl.ambif.ambify.shared.TimetableWeek

class TimetableTextsTest {
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Test
    fun recurrenceInWords() {
        assertEquals("Co tydzień w pn, wt · bez końca", describe(Recurrence(Frequency.WEEKLY, weekdays = listOf(2, 1)), wednesday))
        assertEquals(
            "Co 2 tyg. w śr · do 5.1.2027 · pomija dni wolne",
            describe(Recurrence(Frequency.WEEKLY, 2, endDate = "2027-01-05", skipSchoolFree = true), wednesday),
        )
        assertEquals("Co miesiąc w 1. środę · 10 razy", describe(Recurrence(Frequency.MONTHLY, monthMode = MonthMode.NTH_WEEKDAY, count = 10), wednesday))
        assertEquals("Jednorazowo, 4.11.2026", describe(Recurrence(), wednesday))
    }

    @Test
    fun subjectAbbreviations() {
        assertEquals("ANG", abbreviation("Język angielski"))
        assertEquals("MAT", abbreviation("Matematyka"))
        assertEquals("WF", abbreviation("Wychowanie fizyczne"))
    }

    @Test
    fun onlyMyRemindersArePlanned() {
        val week = TimetableWeek(
            monday = "2026-11-02",
            students = listOf(Student("s1", "Bartosz", "6b")),
            lessons = emptyMap(),
            activities = listOf(
                ExtraActivity(
                    "a1", "s1", "Basen", "Pływalnia", "2026-11-02", "16:00", "17:00",
                    reminders = listOf(Reminder("me", ReminderKind.ALARM, 45), Reminder("other", ReminderKind.NOTIFICATION, 15)),
                ),
            ),
            occurrences = listOf(Occurrence("a1", "2026-11-02"), Occurrence("a1", "2026-11-04")),
        )
        val mine = ReminderScheduler.remindersOf(week, "me")
        assertEquals(2, mine.size)
        assertEquals("Basen za 45 min", mine.first().title)
        assertEquals("Bartosz · 16:00–17:00 · Pływalnia", mine.first().text)
        assertEquals(ReminderKind.ALARM, mine.first().kind)
    }

    @Test
    fun tokensFromEduVulcan() {
        assertEquals(listOf("a.b.c", "d.e.f"), apTokens("""{"Success":true,"Tokens":["a.b.c","d.e.f"],"ErrorMessage":null}"""))
        // Not signed in yet: the sign-in page, or an answer without tokens.
        assertNull(apTokens(""))
        assertNull(apTokens("""{"Success":false,"Tokens":[],"ErrorMessage":"Brak dostępu"}"""))
        assertNull(apTokens("<html>logowanie</html>"))
    }

    @Test
    fun offlineCopyHoldsTheServersRange() {
        // A week back to four weeks ahead, as the server keeps them.
        assertEquals(
            listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23", "2026-11-30"),
            TimetableCache.range(LocalDate.of(2026, 11, 4)).map { it.toString() },
        )
    }
}
