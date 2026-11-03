package pl.ambif.ambify.server.timetable.diary

import java.time.LocalDate
import pl.ambif.ambify.shared.LessonChange

/** A pupil as the diary knows them; [key] identifies them in that diary (stable between updates). */
data class DiaryPupilData(val key: String, val firstName: String, val surname: String, val clazz: String, val school: String)

data class DiaryLesson(
    val date: LocalDate,
    val position: Int,
    val subject: String,
    val room: String,
    val teacher: String,
    val time: String,
    val change: LessonChange? = null,
)

/** A new connection: [credential] (kept encrypted by the server) and the pupils the parent account sees. */
data class DiaryLink(val credential: String, val pupils: List<DiaryPupilData>)

/** Something the diary refused or could not answer; [message] is for the person (Polish). */
class DiaryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A source of the timetable: a school diary a parent account is connected to. eduVulcan for now; other diaries
 * (Librus, Mobidziennik) are further implementations. All calls go over the network: never inside a transaction.
 */
interface SchoolDiary {
    /** Registers with the one-time grant from the diary's site. */
    fun connect(grant: List<String>): DiaryLink
    fun pupils(credential: String): List<DiaryPupilData>
    fun lessons(credential: String, pupilKey: String, from: LocalDate, to: LocalDate): List<DiaryLesson>
    /** Best effort: the connection is forgotten by AMBiFy either way. */
    fun disconnect(credential: String)
}
