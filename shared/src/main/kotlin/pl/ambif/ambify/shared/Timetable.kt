package pl.ambif.ambify.shared

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.Serializable

object TimetablePaths {
    /** GET ?week=YYYY-MM-DD (any day of the week): lessons, extra activities and their dates for that week. */
    const val TIMETABLE = "/timetable"
    const val EXTRAS = "/timetable/extras"
    const val STUDENTS = "/timetable/students"
    /** GET: the community's school diary connections; POST …/{id}/sync, DELETE …/{id}. */
    const val DIARY = "/timetable/diary"
    /** POST [DiaryConnectRequest]: connects an eduVulcan parent account. */
    const val DIARY_VULCAN = "/timetable/diary/vulcan"
}

/** A student from the school diary in a community; [memberId] links them to a member's account (optional). */
@Serializable
data class Student(
    val id: String,
    val name: String,
    val clazz: String,
    val memberId: String? = null,
    val avatar: Avatar? = null,
)

/** One lesson; [date] is ISO (2026-11-02), [time] as the diary gives it ("08:00-08:45"). */
@Serializable
data class Lesson(
    val date: String,
    val position: Int,
    val subject: String,
    val room: String,
    val teacher: String,
    val time: String,
    /** A change from the diary: cancelled, a substitute teacher, another room or day. */
    val change: LessonChange? = null,
)

@Serializable
enum class ChangeKind { CANCELLED, SUBSTITUTION, CHANGED }

/** [note]: what the diary says about it ("Nieobecność nauczyciela", "przeniesiona na 5.11"), may be empty. */
@Serializable
data class LessonChange(val kind: ChangeKind, val note: String = "")

@Serializable
enum class Frequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

@Serializable
enum class MonthMode { DAY_OF_MONTH, NTH_WEEKDAY }

/**
 * When an activity repeats: every [interval] days/weeks/months/years from its first date. Weekly on [weekdays]
 * (1 = Monday … 7 = Sunday; empty = the first date's weekday), monthly on the same day of the month or on the
 * same n-th weekday ("1st Wednesday"). Ends on [endDate] (inclusive) or after [count] times, else never.
 * [skipSchoolFree]: no activity on school days without lessons in the timetable (holidays, days off).
 */
@Serializable
data class Recurrence(
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val weekdays: List<Int> = emptyList(),
    val monthMode: MonthMode = MonthMode.DAY_OF_MONTH,
    val endDate: String? = null,
    val count: Int? = null,
    val skipSchoolFree: Boolean = false,
)

@Serializable
enum class ReminderKind { NOTIFICATION, ALARM }

/** A reminder for one member (on their own phones): a notification or an alarm, [minutesBefore] the start. */
@Serializable
data class Reminder(val memberId: String, val kind: ReminderKind, val minutesBefore: Int)

@Serializable
data class ExtraActivity(
    val id: String,
    val studentId: String,
    val name: String,
    val place: String? = null,
    val startDate: String,
    val startTime: String,
    val endTime: String,
    val recurrence: Recurrence = Recurrence(),
    val reminders: List<Reminder> = emptyList(),
    val addToCalendar: Boolean = false,
    val calendars: List<String> = emptyList(),
    val createdByName: String = "",
)

/** A new activity (or a change of one): for one or more students at once. */
@Serializable
data class ExtraActivityRequest(
    val studentIds: List<String>,
    val name: String,
    val place: String? = null,
    val startDate: String,
    val startTime: String,
    val endTime: String,
    val recurrence: Recurrence = Recurrence(),
    val reminders: List<Reminder> = emptyList(),
    val addToCalendar: Boolean = false,
    val calendars: List<String> = emptyList(),
)

/** An activity on a given date (one occurrence of its recurrence). */
@Serializable
data class Occurrence(val activityId: String, val date: String)

@Serializable
data class TimetableWeek(
    /** Monday of the week shown. */
    val monday: String,
    val students: List<Student>,
    /** Lessons per student id. */
    val lessons: Map<String, List<Lesson>>,
    val activities: List<ExtraActivity>,
    val occurrences: List<Occurrence>,
    /** The community has a school diary connected (else the empty plan explains how to connect it). */
    val diaryConnected: Boolean = false,
    /** Who may connect the diary (names), for the empty plan of those who may not. */
    val diaryLinkers: List<String> = emptyList(),
)

/** Saved at once ([activities]), or waiting for approval ([queued]) when the role may do it only after approval. */
@Serializable
data class ExtraSaveResult(val activities: List<ExtraActivity> = emptyList(), val queued: RequestQueued? = null)

@Serializable
data class StudentLink(val memberId: String?)

@Serializable
enum class DiaryKind { VULCAN }

/** The one-time tokens from https://eduvulcan.pl/api/ap (one per child, valid about an hour). */
@Serializable
data class DiaryConnectRequest(val tokens: List<String>)

@Serializable
data class DiaryPupil(val studentId: String, val name: String, val clazz: String, val school: String = "")

/** A parent account of a school diary connected to the community; [lastError] when the last update failed. */
@Serializable
data class DiaryConnection(
    val id: String,
    val kind: DiaryKind,
    val addedByName: String,
    val pupils: List<DiaryPupil>,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

object Recurrences {
    private const val MAX_STEPS = 5000

    /**
     * Dates of an activity starting on [start] that fall between [from] and [to] (inclusive).
     * [schoolFree] tells which dates have no lessons although they should (used with [Recurrence.skipSchoolFree]).
     */
    fun occurrences(
        start: LocalDate,
        rule: Recurrence,
        from: LocalDate,
        to: LocalDate,
        schoolFree: (LocalDate) -> Boolean = { false },
    ): List<LocalDate> {
        val end = rule.endDate?.let(LocalDate::parse)
        val result = mutableListOf<LocalDate>()
        var made = 0
        for (date in generate(start, rule)) {
            if (date.isAfter(to) || (end != null && date.isAfter(end))) break
            if (rule.count != null && made >= rule.count) break
            made++
            if (date.isBefore(from)) continue
            if (rule.skipSchoolFree && schoolFree(date)) continue
            result += date
        }
        return result
    }

    /** All dates of the rule in order, from [start] on (without the end and the skipped days). */
    private fun generate(start: LocalDate, rule: Recurrence): Sequence<LocalDate> = sequence {
        val step = rule.interval.coerceAtLeast(1).toLong()
        when (rule.frequency) {
            Frequency.NONE -> yield(start)
            Frequency.DAILY -> for (i in 0 until MAX_STEPS) yield(start.plusDays(i * step))
            Frequency.WEEKLY -> {
                val days = rule.weekdays.filter { it in 1..7 }.ifEmpty { listOf(start.dayOfWeek.value) }.sorted()
                val firstMonday = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                for (week in 0 until MAX_STEPS) {
                    val monday = firstMonday.plusWeeks(week * step)
                    for (day in days) {
                        val date = monday.plusDays(day - 1L)
                        if (!date.isBefore(start)) yield(date)
                    }
                }
            }
            Frequency.MONTHLY -> {
                val nth = (start.dayOfMonth - 1) / 7 + 1
                for (i in 0 until MAX_STEPS) {
                    val month = start.withDayOfMonth(1).plusMonths(i * step)
                    val date = when (rule.monthMode) {
                        MonthMode.DAY_OF_MONTH -> if (start.dayOfMonth <= month.lengthOfMonth()) month.withDayOfMonth(start.dayOfMonth) else null
                        MonthMode.NTH_WEEKDAY -> month.with(TemporalAdjusters.dayOfWeekInMonth(nth, start.dayOfWeek))
                            .takeIf { it.month == month.month }
                    }
                    if (date != null && !date.isBefore(start)) yield(date)
                }
            }
            Frequency.YEARLY -> for (i in 0 until MAX_STEPS) {
                val year = start.plusYears(i * step)
                if (year.dayOfMonth == start.dayOfMonth) yield(year)
            }
        }
    }

    /** Monday of the week [date] is in. */
    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
