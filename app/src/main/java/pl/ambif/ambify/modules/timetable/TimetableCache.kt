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

    fun clear(context: Context) {
        File(context.filesDir, DIR).deleteRecursively()
    }

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
