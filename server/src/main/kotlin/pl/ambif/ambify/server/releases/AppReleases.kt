package pl.ambif.ambify.server.releases

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import kotlinx.serialization.json.Json
import pl.ambif.ambify.server.Diagnostics
import pl.ambif.ambify.shared.ApiError
import pl.ambif.ambify.shared.AppPaths
import pl.ambif.ambify.shared.AppRelease

/**
 * The published app, as `publish-app.sh` leaves it in [dir]: the APK files and `latest.json` about the newest one.
 * Read on every request, so publishing needs no server restart.
 */
class AppReleases(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Kanał kids (telefony dzieci) leży w podkatalogu `kids/`. */
    private val kidsDir = dir.resolve(KIDS_DIR)

    /** The newest release; never lets an app older than the server supports go on without updating. */
    fun latest(): AppRelease? = latestIn(dir)

    /** One published APK; only plain names made by the publish script, never a path out of [dir]. */
    fun apk(name: String): File? = apkIn(dir, name)

    /** To samo, ale dla kanału kids. */
    fun kidsLatest(): AppRelease? = latestIn(kidsDir)

    fun kidsApk(name: String): File? = apkIn(kidsDir, name)

    private fun latestIn(channel: File): AppRelease? {
        val file = channel.resolve(LATEST)
        if (!file.isFile) return null
        val release = runCatching { json.decodeFromString<AppRelease>(file.readText()) }.getOrNull() ?: return null
        return release.copy(minVersionCode = maxOf(release.minVersionCode, Diagnostics.MIN_APP_VERSION_CODE))
    }

    private fun apkIn(channel: File, name: String): File? =
        channel.resolve(name).takeIf { APK_NAME.matches(name) && it.isFile }

    companion object {
        const val LATEST = "latest.json"
        const val KIDS_DIR = "kids"
        val APK_NAME = Regex("ambify-[0-9A-Za-z._-]+\\.apk")
    }
}

fun Route.releaseRoutes(releases: AppReleases) {
    // Zwykły kanał.
    channelRoutes(AppPaths.LATEST, AppPaths.APK, AppPaths.DOWNLOAD, kids = false, releases::latest, releases::apk)
    // Kanał kids — build dla telefonów dzieci, osobny plik latest.json i APK.
    channelRoutes(AppPaths.KIDS_LATEST, AppPaths.KIDS_APK, AppPaths.KIDS_DOWNLOAD, kids = true, releases::kidsLatest, releases::kidsApk)
}

private fun Route.channelRoutes(
    latestPath: String,
    apkPath: String,
    downloadPath: String,
    kids: Boolean,
    latest: () -> AppRelease?,
    apk: (String) -> File?,
) {
    get(latestPath) {
        val release = latest()
        if (release == null) call.respond(HttpStatusCode.NotFound, ApiError("no_release", "Aplikacja nie jest jeszcze opublikowana."))
        else call.respond(release)
    }
    get("$apkPath/{name}") {
        val file = apk(call.parameters["name"].orEmpty())
        if (file == null) {
            call.respond(HttpStatusCode.NotFound, ApiError("not_found", "Nie ma takiego pliku."))
            return@get
        }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, file.name).toString(),
        )
        call.respondFile(file)
    }
    get(downloadPath) {
        call.respondText(downloadPage(latest(), apkPath, kids), ContentType.Text.Html.withParameter("charset", "utf-8"))
    }
}

private val Months = listOf(
    "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
    "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
)

/** "2026-11-03" → "3 listopada 2026". */
internal fun polishDate(iso: String): String {
    val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3 || parts[1] !in 1..12) return iso
    return "${parts[2]} ${Months[parts[1] - 1]} ${parts[0]}"
}

private fun String.escaped() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * The page from the mock-up: a gold download button and the install steps, readable on a phone.
 * [apkPath] is the APK base path of this channel; [kids] serves the children's build (osobny tytuł i ostrzeżenie
 * dla rodzica — bez informowania dziecka).
 */
internal fun downloadPage(release: AppRelease?, apkPath: String = AppPaths.APK, kids: Boolean = false): String {
    val button = if (release == null) {
        """<p class="muted">Aplikacja nie jest jeszcze opublikowana. Zajrzyj tu później.</p>"""
    } else {
        val size = "%.0f MB".format(release.sizeBytes / 1_048_576.0)
        val date = if (release.publishedAt.isBlank()) "" else " · ${polishDate(release.publishedAt)}"
        """<a class="gold" href="$apkPath/${release.file.escaped()}" download>Pobierz AMBiFy ${release.versionName.escaped()}</a>
        <p class="muted">Android 12 lub nowszy · $size$date</p>"""
    }
    val subtitle = if (kids) "Wersja dla telefonu dziecka" else "Aplikacja naszej społeczności"
    val warning = if (kids) {
        """<section class="card warn"><p class="muted" style="color:#E4DFD3">Ta wersja zapisuje zdarzenia telefonu dla rodzica. <b>Instaluj tylko na telefonie dziecka.</b> Na pozostałe telefony jest zwykła strona pobierania.</p></section>"""
    } else {
        ""
    }
    val lastStep = if (kids) "Otwórz AMBiFy i zaloguj dziecko." else "Otwórz AMBiFy, załóż konto i wpisz kod zaproszenia, który dostałeś."
    return """<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pobierz AMBiFy</title>
<style>
body{margin:0;background:#121214;color:#F1ECE0;font-family:Roboto,system-ui,sans-serif}
main{max-width:480px;margin:0 auto;padding:28px 16px 32px;display:flex;flex-direction:column;gap:18px}
.logo{text-align:center;font-size:34px;font-weight:700;letter-spacing:4px;color:#E6C65C;margin:0}
.sub{text-align:center;color:#A9A9B1;margin:6px 0 0;font-size:14px}
.card{border:1px solid #8C7322;border-radius:20px;background:linear-gradient(180deg,#232327,#19191C);padding:20px;display:flex;flex-direction:column;gap:14px}
.warn{border-color:#D4AF37;background:rgba(212,175,55,.08)}
.center{align-items:center;text-align:center}
.gold{display:inline-block;padding:16px 28px;border-radius:28px;background:linear-gradient(180deg,#F7E08A,#D4AF37 55%,#A88A2A);color:#19191C;font-size:16px;font-weight:700;text-decoration:none}
.muted{color:#A9A9B1;font-size:13px;margin:0;line-height:1.5}
.label{font-size:12px;font-weight:700;letter-spacing:1.6px;color:#E6C65C;margin:0}
ol{margin:0;padding-left:22px;display:flex;flex-direction:column;gap:12px;font-size:15px;line-height:1.5;color:#E4DFD3}
</style>
</head>
<body>
<main>
<div><p class="logo">AMBiFy</p><p class="sub">$subtitle</p></div>
$warning
<section class="card center">
$button
</section>
<section class="card">
<p class="label">JAK ZAINSTALOWAĆ</p>
<ol>
<li>Stuknij „Pobierz”, a gdy plik się pobierze, otwórz go.</li>
<li>Android zapyta, czy przeglądarka może instalować aplikacje. Wybierz „Ustawienia”, włącz „Zezwalaj z tego źródła” i wróć.</li>
<li>Stuknij „Zainstaluj”. Gdy aplikacja zapyta o dostęp do sieci lokalnej, wybierz „Zezwól”.</li>
<li>$lastStep</li>
</ol>
</section>
<p class="muted" style="text-align:center">Strona działa tylko w domowym Wi‑Fi. Kolejne wersje aplikacja pobierze już sama.</p>
</main>
</body>
</html>
"""
}
