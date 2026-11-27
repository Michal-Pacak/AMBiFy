package pl.ambif.ambify.features.reports

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.core.network.TokenStore
import pl.ambif.ambify.shared.MyReport
import pl.ambif.ambify.shared.NewReport
import pl.ambif.ambify.shared.ReportRules
import pl.ambif.ambify.shared.ReportStatus

/**
 * 0.7.9: what "Zgłoś błąd" starts with — the screen whose "?" was open and its screenshot, taken when "?" was
 * tapped (before the help window covered it). Taken by the report screen once.
 */
object ReportDraft {
    @Volatile var screen: String? = null
        private set
    @Volatile var shot: Bitmap? = null
        private set

    fun set(screen: String?, shot: Bitmap?) {
        this.screen = screen
        this.shot = shot
    }

    fun take(): Pair<String?, Bitmap?> = (screen to shot).also { set(null, null) }
}

/** The window as it is now, in [onDone] (null when Android refuses); scaled to at most [MAX_SIDE] px. */
fun captureScreen(context: Context, onDone: (Bitmap?) -> Unit) {
    val window = context.activity()?.window ?: return onDone(null)
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) return onDone(null)
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    runCatching {
        PixelCopy.request(window, bitmap, { result ->
            onDone(if (result == PixelCopy.SUCCESS) bitmap.scaledTo(MAX_SIDE) else null)
        }, Handler(Looper.getMainLooper()))
    }.onFailure { onDone(null) }
}

private const val MAX_SIDE = 1600

private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
    val side = maxOf(width, height)
    if (side <= maxSide) return this
    val scale = maxSide.toFloat() / side
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** The phone's part of a report (board 1): version, model, Android, network. */
fun newReport(context: Context, text: String, screen: String?, shots: Int) = NewReport(
    clientId = UUID.randomUUID().toString(),
    text = text.trim(),
    screen = screen,
    appVersion = BuildConfig.VERSION_NAME,
    appCode = BuildConfig.VERSION_CODE,
    phone = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
    android = Build.VERSION.RELEASE,
    kids = BuildConfig.KIDS,
    network = network(context),
    createdAt = System.currentTimeMillis(),
    shots = shots,
)

private fun network(context: Context): String {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "?"
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "brak"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "komórkowa"
        else -> "inna"
    }
}

/**
 * Reports waiting for the network (board 2, 4): `filesDir/report-outbox/<clientId>.json` and its screenshots
 * `<clientId>-<n>.jpg`. Sent by [send] at once, or later by [ReportsSyncWorker].
 */
class ReportOutbox private constructor(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Saves [report] with its [shots] (JPEG files, moved here). */
    fun add(report: NewReport, shots: List<File>) {
        dir.mkdirs()
        shots.take(ReportRules.MAX_SHOTS).forEachIndexed { i, file ->
            val target = dir.resolve("${report.clientId}-$i.jpg")
            if (!file.renameTo(target)) { file.copyTo(target, overwrite = true); file.delete() }
        }
        dir.resolve("${report.clientId}.json").writeText(json.encodeToString(NewReport.serializer(), report.copy(shots = shots.size.coerceAtMost(ReportRules.MAX_SHOTS))))
    }

    fun pending(): List<NewReport> = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.lastModified() }
        .mapNotNull { runCatching { json.decodeFromString(NewReport.serializer(), it.readText()) }.getOrNull() }

    /** Sends everything waiting; true = nothing left. A network error stops and keeps the rest. */
    suspend fun send(api: ServerApi): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            for (report in pending()) {
                try {
                    val sent = api.sendReport(report)
                    for (i in 0 until report.shots) {
                        val file = dir.resolve("${report.clientId}-$i.jpg")
                        if (file.isFile) api.uploadReportShot(sent.id, i, file)
                    }
                } catch (e: ServerError) {
                    if (e.code == "network" || e.code == "unauthorized" || e.code == "session_expired") return@withContext false
                    // Refused for good (e.g. too long): nothing to wait for.
                }
                dir.resolve("${report.clientId}.json").delete()
                for (i in 0 until ReportRules.MAX_SHOTS) dir.resolve("${report.clientId}-$i.jpg").delete()
            }
            true
        }
    }

    companion object {
        private val lock = Mutex()
        fun of(context: Context) = ReportOutbox(File(context.filesDir, "report-outbox"))
    }
}

/** Sends waiting reports as soon as there is a network (also with the app closed). */
class ReportsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val api = backgroundApi(applicationContext) ?: return Result.success()
        return if (ReportOutbox.of(applicationContext).send(api)) Result.success() else Result.retry()
    }

    companion object {
        fun sendWhenOnline(context: Context) {
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<ReportsSyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork("reports-send", ExistingWorkPolicy.REPLACE, request)
        }

        private fun backgroundApi(context: Context): ServerApi? {
            val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
            if (prefs.getString("refresh_token", null) == null) return null
            val store = object : TokenStore {
                override var accessToken: String?
                    get() = prefs.getString("access_token", null)
                    set(value) { prefs.edit().putString("access_token", value).commit() }
                override var refreshToken: String?
                    get() = prefs.getString("refresh_token", null)
                    set(value) { prefs.edit().putString("refresh_token", value).commit() }
                override fun onSessionExpired() {}
            }
            return ServerApi(tokens = store, community = { prefs.getString("community", null) })
        }
    }
}

/**
 * My reports as last fetched (null = not yet) and the ones waiting on the phone, for Ustawienia and the update
 * popup. [news]: a report turned "Naprawione" or "Wyjaśnione" since I last looked (the gold dot, board 3).
 */
object ReportsStore {
    private val _mine = MutableStateFlow<List<MyReport>?>(null)
    val mine: StateFlow<List<MyReport>?> = _mine.asStateFlow()
    private val _waiting = MutableStateFlow<List<NewReport>>(emptyList())
    val waiting: StateFlow<List<NewReport>> = _waiting.asStateFlow()

    suspend fun refresh(context: Context, api: ServerApi) {
        val outbox = ReportOutbox.of(context)
        if (outbox.pending().isNotEmpty()) outbox.send(api)
        _waiting.value = outbox.pending()
        runCatching { api.myReports() }.onSuccess { _mine.value = it }
    }

    fun waitingChanged(context: Context) {
        _waiting.value = ReportOutbox.of(context).pending()
    }

    fun clear() {
        _mine.value = null
        _waiting.value = emptyList()
    }

    fun news(reports: List<MyReport>?, seenAt: Long): Boolean =
        reports.orEmpty().any { (it.status == ReportStatus.FIXED || it.status == ReportStatus.EXPLAINED) && it.statusAt > seenAt }

    /** My reports fixed in [version] (the line in "Co nowego", board 3). */
    fun fixedIn(version: String): List<MyReport> =
        _mine.value.orEmpty().filter { it.fixedIn == version && (it.status == ReportStatus.IN_FIX || it.status == ReportStatus.FIXED) }
}
