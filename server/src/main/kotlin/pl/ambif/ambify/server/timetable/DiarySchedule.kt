package pl.ambif.ambify.server.timetable

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The daily update of the timetables from the school diaries, at 18:00 Polish time (Michał's choice: once a day is enough). */
object DiarySchedule {
    private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
    private val AT: LocalTime = LocalTime.of(18, 0)

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
        var next = now.with(AT).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
