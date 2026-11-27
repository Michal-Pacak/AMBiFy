package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/**
 * 0.7.9: bug reports sent from the app (canvas "AMBiFy 0.7.9 · Zgłaszanie błędów"). The reporter sees only their own
 * reports and never whether the report came from the kids' variant; the Platform Admin sees everything.
 */
object ReportPaths {
    /** POST [NewReport] → [MyReport] (the same [NewReport.clientId] again returns the first one); GET my reports. */
    const val REPORTS = "/reports"
    /** GET everything sent, newest first (Platform Admin). */
    const val PLATFORM = "/platform/reports"

    /** PUT a JPEG screenshot number [index] (the reporter, right after sending); GET it (reporter or Platform Admin). */
    fun shot(reportId: String, index: Int) = "$REPORTS/$reportId/shots/$index"

    /** PUT [ReportUpdate] (Platform Admin). */
    fun platform(reportId: String) = "$PLATFORM/$reportId"
}

object ReportRules {
    const val MAX_TEXT = 2000
    const val MAX_SHOTS = 3
    const val MAX_SHOT_BYTES = 5L * 1024 * 1024
    /** "To nie błąd" always says why: no bare "Wyjaśnione" (board 7). */
    const val MIN_EXPLANATION = 10
    const val MAX_EXPLANATION = 600
}

@Serializable
enum class ReportStatus(val label: String) {
    SENT("Wysłane"),
    ACCEPTED("Przyjęte"),
    IN_FIX("W naprawie"),
    FIXED("Naprawione"),
    EXPLAINED("Wyjaśnione"),
}

/** What the phone adds by itself (board 1): the person and circle come from the session. */
@Serializable
data class NewReport(
    /** Made on the phone; a report waiting offline and sent twice is kept once. */
    val clientId: String,
    val text: String,
    /** The screen whose "?" was open, e.g. "Lista"; null from Ustawienia. */
    val screen: String? = null,
    val appVersion: String,
    val appCode: Int,
    val phone: String,
    val android: String,
    /** Only the Platform Admin sees it. */
    val kids: Boolean = false,
    /** "Wi-Fi", "komórkowa", "brak". */
    val network: String? = null,
    /** When it was written (it may be sent later). */
    val createdAt: Long,
    /** How many screenshots will follow. */
    val shots: Int = 0,
)

@Serializable
data class ReportStep(val status: ReportStatus, val at: Long, val version: String? = null)

/** One of my reports, as the reporter sees it (boards 3 and 7). */
@Serializable
data class MyReport(
    val id: String,
    val text: String,
    val screen: String? = null,
    val createdAt: Long,
    val status: ReportStatus,
    /** When [status] was set: the dot in Ustawienia shows a FIXED or EXPLAINED one newer than the last look. */
    val statusAt: Long,
    /** The version with the fix (IN_FIX and FIXED). */
    val fixedIn: String? = null,
    /** EXPLAINED: the team's answer. */
    val explanation: String? = null,
    /** Paths of the screenshots. */
    val shots: List<String> = emptyList(),
    val history: List<ReportStep> = emptyList(),
    /** B-009: what the app sent along (board 8). Never the kids' variant. Null from servers before 0.7.9. */
    val appVersion: String? = null,
    val appCode: Int? = null,
    val phone: String? = null,
    val android: String? = null,
    val network: String? = null,
)

/** A report in the Platform Admin's panel (board 4). */
@Serializable
data class PlatformReport(
    val report: MyReport,
    val reporterName: String,
    val circle: String? = null,
    val appVersion: String,
    val appCode: Int,
    val phone: String,
    val android: String,
    val kids: Boolean,
    val network: String? = null,
    /** B-NNN once it is in the bug register. */
    val number: String? = null,
)

/**
 * The Platform Admin changes a report: ACCEPTED (with [number]), IN_FIX (with [fixedIn]; it turns FIXED by itself
 * once that version is published) or EXPLAINED (with [explanation]).
 */
@Serializable
data class ReportUpdate(
    val status: ReportStatus,
    val number: String? = null,
    val fixedIn: String? = null,
    val explanation: String? = null,
)

/** "0.7.9" ≤ "0.7.10" ≤ "0.7.91": compared number by number. */
fun versionAtLeast(version: String, wanted: String): Boolean {
    val a = version.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    val b = wanted.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return true
}
