package pl.ambif.ambify.features.home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.ambif.ambify.shared.ChangeKind
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.LessonChange

class LessonsDayTest {
    // Tuesday 17.11.2026, Friday 20.11, Monday 23.11.
    private val tuesday = LocalDate.of(2026, 11, 17)
    private val wednesday = tuesday.plusDays(1)
    private val friday = LocalDate.of(2026, 11, 20)
    private val monday = LocalDate.of(2026, 11, 23)

    private fun lesson(date: LocalDate, position: Int, time: String, cancelled: Boolean = false) =
        Lesson(date.toString(), position, "Lekcja $position", "12", "", time, if (cancelled) LessonChange(ChangeKind.CANCELLED) else null)

    private fun day(date: LocalDate, vararg times: String) = times.mapIndexed { i, t -> lesson(date, i + 1, t) }

    private val week = mapOf(
        tuesday to day(tuesday, "08:00-08:45", "08:55-09:40", "10:55-11:40", "11:50-12:35", "12:45-13:30"),
        wednesday to listOf(lesson(wednesday, 1, "08:00-08:45", cancelled = true), lesson(wednesday, 2, "08:55-09:40")),
        friday to day(friday, "08:00-08:45", "08:55-09:40"),
        monday to day(monday, "08:55-09:40", "09:50-10:35"),
    )

    private fun at(date: LocalDate, time: String) = LocalDateTime.of(date, LocalTime.parse(time))

    @Test
    fun duringSchoolShowsRunningLessonAndEndToday() {
        val shown = LessonsDays.of(week, at(tuesday, "11:00"))!!
        assertEquals(tuesday, shown.date)
        assertEquals(2, shown.firstShown)
        assertEquals("KONIEC LEKCJI DZIŚ", shown.footerLabel)
        assertEquals("13:30", shown.footerTime)
        assertEquals("DZIŚ", shown.dayTag)
    }

    @Test
    fun beforeSchoolStartsFromTheFirstLesson() {
        assertEquals(0, LessonsDays.of(week, at(tuesday, "07:10"))!!.firstShown)
    }

    @Test
    fun afterSchoolShowsTomorrowsStartWithoutTheCancelledFirstLesson() {
        val shown = LessonsDays.of(week, at(tuesday, "14:00"))!!
        assertEquals(wednesday, shown.date)
        assertEquals("START JUTRO · ŚRODA", shown.footerLabel)
        assertEquals("8:55", shown.footerTime)
        assertEquals("JUTRO", shown.dayTag)
    }

    @Test
    fun fridayAfterSchoolShowsMonday() {
        val shown = LessonsDays.of(week, at(friday, "10:00"))!!
        assertEquals(monday, shown.date)
        assertEquals("START W PONIEDZIAŁEK 23.11", shown.footerLabel)
        assertEquals("8:55", shown.footerTime)
        assertEquals("PN 23.11", shown.dayTag)
    }

    @Test
    fun lastLessonCancelledEndsEarlier() {
        val days = mapOf(tuesday to day(tuesday, "08:00-08:45", "08:55-09:40") + lesson(tuesday, 3, "09:50-10:35", cancelled = true))
        assertEquals("9:40", LessonsDays.of(days, at(tuesday, "08:10"))!!.footerTime)
        assertNull(LessonsDays.of(days, at(tuesday, "09:45")))
    }

    @Test
    fun moreLabels() {
        assertEquals("+ 1 kolejna", LessonsDays.moreLabel(1))
        assertEquals("+ 3 kolejne", LessonsDays.moreLabel(3))
        assertEquals("+ 5 kolejnych", LessonsDays.moreLabel(5))
        assertEquals("+ 12 kolejnych", LessonsDays.moreLabel(12))
    }
}
