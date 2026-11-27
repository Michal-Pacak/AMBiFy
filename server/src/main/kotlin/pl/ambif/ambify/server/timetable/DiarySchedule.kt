package pl.ambif.ambify.server.timetable

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The updates of the timetables from the school diaries, Polish time: 18:00 (Michał's first choice) and 4:00
 * (B-014: substitutions entered in the evening must be there in the morning; nobody adds them between 4 and 6).
 */
object DiarySchedule {
    private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
    private val AT: List<LocalTime> = listOf(LocalTime.of(4, 0), LocalTime.of(18, 0))

    fun start(timetable: TimetableService) {
        val executor = Executors.newSingleThreadScheduledExecutor { Thread(it, "diary-sync").apply { isDaemon = true } }
        fun planNext() {
            executor.schedule({
                runCatching { timetable.syncAllDiaries() }
                planNext()
            }, delayToNext(ZonedDateTime.now(WARSAW)).toMillis(), TimeUnit.MILLISECONDS)
        }
        planNext()
    }

    internal fun delayToNext(now: ZonedDateTime): Duration {
        val next = AT.map { at ->
            val today = now.with(at).withSecond(0).withNano(0)
            if (today.isAfter(now)) today else today.plusDays(1)
        }.minOf { it }
        return Duration.between(now, next)
    }
}
