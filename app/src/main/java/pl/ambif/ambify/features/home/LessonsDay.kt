package pl.ambif.ambify.features.home

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import pl.ambif.ambify.features.timetable.cancelled
import pl.ambif.ambify.features.timetable.dayMonth
import pl.ambif.ambify.features.timetable.short
import pl.ambif.ambify.features.timetable.span
import pl.ambif.ambify.shared.Lesson
import pl.ambif.ambify.shared.WidgetHeight

/**
 * What the "Lekcje" widget shows for one person: the school day ([date]) with all its [lessons] in order, from
 * which lesson the folded widget starts ([firstShown]: the running or next one today, the first one on a coming
 * day), and the short line at the bottom (widgets use short day names: "START PN 23.11"): when lessons end today, or — after lessons, at the weekend or before a
 * holiday — when they start on the next school day.
 */
data class LessonsDay(
    val date: LocalDate,
    val today: Boolean,
    val lessons: List<Lesson>,
    val firstShown: Int,
    val footerLabel: String,
    val footerTime: String,
    /** "DZIŚ", "JUTRO" or "PN 24.11": for the widget's title. */
    val dayTag: String,
)

object LessonsDays {
    /**
     * The day to show from [days] (lessons by date, as many weeks as the phone has): today while it still has a
     * lesson that is not cancelled ahead or running, else the first later day with such a lesson. Null when
     * there is none (holidays longer than the weeks at hand, or no plan).
     */
    fun of(days: Map<LocalDate, List<Lesson>>, now: LocalDateTime): LessonsDay? {
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val todays = days[today].orEmpty().sortedBy { it.position }
        val todayEnd = todays.filterNot { it.cancelled }.mapNotNull { it.span()?.second }.maxOrNull()
        if (todayEnd != null && time.isBefore(todayEnd)) {
            val first = todays.indexOfFirst { lesson -> lesson.span()?.second?.isAfter(time) ?: true }.coerceAtLeast(0)
            return LessonsDay(today, true, todays, first, "KONIEC DZIŚ", todayEnd.short(), "DZIŚ")
        }
        val next = days.keys.filter { it.isAfter(today) }.sorted()
            .firstOrNull { date -> days[date].orEmpty().any { !it.cancelled && it.span() != null } } ?: return null
        val lessons = days[next].orEmpty().sortedBy { it.position }
        val start = lessons.filterNot { it.cancelled }.mapNotNull { it.span()?.first }.minOrNull() ?: LocalTime.MIDNIGHT
        val tomorrow = next == today.plusDays(1)
        val weekday = next.dayOfWeek.value - 1
        val label = if (tomorrow) "START JUTRO" else "START ${DayTag[weekday]} ${next.dayMonth()}"
        val tag = if (tomorrow) "JUTRO" else "${DayTag[weekday]} ${next.dayMonth()}"
        return LessonsDay(next, false, lessons, 0, label, start.short(), tag)
    }

    /** How many lessons the folded widget shows at each height. */
    fun count(height: WidgetHeight): Int = when (height) {
        WidgetHeight.SMALL -> 1
        WidgetHeight.MEDIUM -> 3
        WidgetHeight.LARGE -> 5
    }

    /** "+ 1 kolejna", "+ 3 kolejne", "+ 5 kolejnych". */
    fun moreLabel(count: Int): String = "+ $count " + when {
        count == 1 -> "kolejna"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "kolejne"
        else -> "kolejnych"
    }

    private val DayTag = listOf("PN", "WT", "ŚR", "CZW", "PT", "SOB", "ND")
}
