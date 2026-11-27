package pl.ambif.ambify.modules.timetable

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.shared.Recurrences
import pl.ambif.ambify.shared.TimetableWeek

/** A week as the phone last got it, and when ([savedAt], epoch millis). */
@Serializable
data class SavedWeek(val savedAt: Long, val week: TimetableWeek)

/**
 * The offline copy of the timetable: the weeks of the server's range (a week back to four ahead, about 10 KB each),
 * one file per community and week. Shown when the server cannot be reached; only for reading (adding or changing
 * extra activities needs the server). Weeks older than a week back are removed; signing out removes everything.
 */
object TimetableCache {
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, communityId: String, week: TimetableWeek, now: Long = System.currentTimeMillis()) {
        val dir = dir(context, communityId).apply { mkdirs() }
        runCatching { File(dir, "${week.monday}.json").writeText(json.encodeToString(SavedWeek(now, week))) }
        prune(dir)
    }

    fun load(context: Context, communityId: String, monday: String): SavedWeek? =
        File(dir(context, communityId), "$monday.json").takeIf { it.exists() }
            ?.let { runCatching { json.decodeFromString<SavedWeek>(it.readText()) }.getOrNull() }
            ?.let(::withLessonsConnected)

    /**
     * A copy with lessons in it came from a connected diary, even when its flag says otherwise (a copy saved by an
     * older version, before the flag existed): offline it must show the plan, never "connect the timetable".
     */
    internal fun withLessonsConnected(saved: SavedWeek): SavedWeek =
        if (saved.week.diaryConnected || saved.week.lessons.values.all { it.isEmpty() }) saved
        else saved.copy(week = saved.week.copy(diaryConnected = true))

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

    /** The communities whose timetable this phone keeps a copy of (refreshed at night, B-014). */
    fun communities(context: Context): List<String> =
        File(context.filesDir, DIR).listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    /** The Mondays the copy should hold, from a week back to four weeks ahead of [today]. */
    fun range(today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = Recurrences.mondayOf(today)
        return (-1L..4L).map { monday.plusWeeks(it) }
    }

    private fun prune(dir: File) {
        val oldest = range().first().toString()
        dir.listFiles()?.filter { it.name.removeSuffix(".json") < oldest }?.forEach { it.delete() }
    }

    private fun dir(context: Context, communityId: String) = File(File(context.filesDir, DIR), communityId)

    private const val DIR = "timetable"
}
