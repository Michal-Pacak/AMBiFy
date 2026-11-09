package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/** Downloading the app and its updates from the home server; open without signing in. */
object AppPaths {
    const val LATEST = "/app/latest"
    const val APK = "/app/apk"
    /** The page sent to a new person: a download button and how to install. */
    const val DOWNLOAD = "/pobierz"

    /**
     * Osobny kanał "kids" — build dla telefonów dzieci. Ten build aktualizuje się TYLKO z tego kanału, żeby nie
     * zassać zwykłego APK (ten sam applicationId) i nie zgubić zbierania zdarzeń.
     */
    const val KIDS_LATEST = "/app/kids/latest"
    const val KIDS_APK = "/app/kids/apk"
    const val KIDS_DOWNLOAD = "/pobierz/kids"

    /** Ścieżka do najnowszej wersji / katalog APK dla danego kanału. */
    fun latest(kids: Boolean) = if (kids) KIDS_LATEST else LATEST
    fun apk(kids: Boolean) = if (kids) KIDS_APK else APK
}

/**
 * The newest published app ([AppPaths.LATEST]). An app older than [minVersionCode] no longer works with this server
 * and may only update.
 */
@Serializable
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    /** "Co nowego", one point per line. */
    val notes: List<String> = emptyList(),
    /** File name under [AppPaths.APK]. */
    val file: String,
    val sha256: String,
    val sizeBytes: Long,
    val minVersionCode: Int = 0,
    /** ISO date, e.g. "2026-11-03". */
    val publishedAt: String = "",
)
