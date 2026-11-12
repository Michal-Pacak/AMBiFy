package pl.ambif.ambify.modules.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.shared.AppRelease

/** Where the app is with its own update; the Dashboard card, the Aplikacja card and the forced screen show it. */
sealed interface UpdateState {
    /** Not asked yet, or the server is out of reach. */
    data object Unknown : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    /** Downloaded, waiting for the night ([UpdateRules.nightInstallAt]). */
    data class Scheduled(val release: AppRelease) : UpdateState
    /** Handed to Android; it may still ask "Aktualizować?". */
    data class Installing(val release: AppRelease) : UpdateState
    /** Android does not yet let AMBiFy install its updates: the explanation screen, then [UpdateState.Available] action again. */
    data class NeedsPermission(val release: AppRelease, val atNight: Boolean) : UpdateState
    data class Failed(val release: AppRelease, val message: String) : UpdateState
}

/** The decisions of the updater, without Android, so they can be tested. */
object UpdateRules {
    /** Between automatic checks when the app comes back to the screen. */
    const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

    fun isNewer(release: AppRelease, installed: Int = BuildConfig.VERSION_CODE) = release.versionCode > installed

    /** This app no longer works with the server: only updating is left. */
    fun isForced(release: AppRelease, installed: Int = BuildConfig.VERSION_CODE) = installed < release.minVersionCode

    fun shouldCheck(now: Long, lastChecked: Long, checkedThisRun: Boolean) =
        !checkedThisRun || now - lastChecked >= CHECK_EVERY_MS

    /** The next 3:00 — when the phone usually lies unused. */
    fun nightInstallAt(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(3, 0).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Checks the home server for a newer AMBiFy, downloads it and installs it through Android's [PackageInstaller]
 * — now, or at night. One per app process ([of]): the install result comes back to [UpdateReceiver].
 */
class AppUpdater private constructor(private val context: Context) {
    private val api = ServerApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var checkedThisRun = false

    private val _state = MutableStateFlow<UpdateState>(scheduledRelease()?.let { UpdateState.Scheduled(it) } ?: UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The update popup; opens by itself once per run of the app when a newer version is found. */
    private val _popupOpen = MutableStateFlow(false)
    val popupOpen: StateFlow<Boolean> = _popupOpen.asStateFlow()
    private var popupShownThisRun = false

    /** The newest release the server reported, also when this app is up to date (for the forced screen). */
    private val _latest = MutableStateFlow<AppRelease?>(null)
    val latest: StateFlow<AppRelease?> = _latest.asStateFlow()

    val lastChecked: Long get() = prefs.getLong(KEY_CHECKED, 0L)

    /** The app came to the screen: checks again if it is time, and carries on after the permission screen. */
    fun onResume() {
        noticeAbandonedInstall()
        (_state.value as? UpdateState.NeedsPermission)?.let { waiting ->
            if (canInstall()) {
                if (waiting.atNight) installAtNight(waiting.release) else installNow(waiting.release)
                return
            }
        }
        if (UpdateRules.shouldCheck(System.currentTimeMillis(), lastChecked, checkedThisRun)) check()
    }

    /** Asks the server; [onDone] gets whether a newer version is there (null: no answer). */
    fun check(onDone: (Boolean?) -> Unit = {}) {
        scope.launch {
            val release = runCatching { api.latestRelease() }.getOrElse {
                // Nothing published yet is "up to date"; no server leaves the last state.
                if ((it as? ServerError)?.code == "no_release") {
                    markChecked()
                    if (_state.value == UpdateState.Unknown) _state.value = UpdateState.UpToDate
                    onDone(false)
                } else {
                    onDone(null)
                }
                return@launch
            }
            markChecked()
            _latest.value = release
            val newer = UpdateRules.isNewer(release)
            _state.value = when (val current = _state.value) {
                // Busy with this very version: leave it be.
                is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.NeedsPermission -> current
                is UpdateState.Scheduled -> if (current.release.versionCode == release.versionCode) current else available(release)
                else -> if (newer) UpdateState.Available(release) else UpdateState.UpToDate
            }
            if (_state.value is UpdateState.Available && !popupShownThisRun) openPopup()
            onDone(newer)
        }
    }

    private fun available(release: AppRelease): UpdateState {
        cancelNight()
        return if (UpdateRules.isNewer(release)) UpdateState.Available(release) else UpdateState.UpToDate
    }

    private fun markChecked() {
        checkedThisRun = true
        prefs.edit { putLong(KEY_CHECKED, System.currentTimeMillis()) }
    }

    fun openPopup() {
        popupShownThisRun = true
        _popupOpen.value = true
    }

    /** "Później": the popup closes, the pulsing "Aktualizacja" button at the top stays. */
    fun closePopup() {
        _popupOpen.value = false
    }

    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    /** Android's own screen with the "Zezwalaj z tego źródła" switch for AMBiFy. */
    fun permissionSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Back from the permission screen without allowing it. */
    fun cancelPermission() {
        (_state.value as? UpdateState.NeedsPermission)?.let { _state.value = UpdateState.Available(it.release) }
    }

    fun installNow(release: AppRelease) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, atNight = false)
            return
        }
        scope.launch {
            val file = download(release) ?: return@launch
            _state.value = UpdateState.Installing(release)
            commit(file, atNight = false)
        }
    }

    fun installAtNight(release: AppRelease) {
        if (!canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, atNight = true)
            return
        }
        scope.launch {
            download(release) ?: return@launch
            prefs.edit { putString(KEY_SCHEDULED, json.encodeToString(release)) }
            planNight()
            updateLog(context, "zaplanowano ${release.versionName} na noc")
            _state.value = UpdateState.Scheduled(release)
        }
    }

    fun cancelNight() {
        val release = scheduledRelease()
        if (release != null) updateLog(context, "anulowano noc")
        context.getSystemService(AlarmManager::class.java).cancel(nightIntent())
        prefs.edit { remove(KEY_SCHEDULED) }
        if (release != null && _state.value is UpdateState.Scheduled) _state.value = UpdateState.Available(release)
    }

    /** The night alarm went off ([UpdateReceiver]): installs the downloaded version, if it is still whole. */
    internal fun installScheduled() {
        val release = scheduledRelease()
        updateLog(context, "alarm nocny" + if (release == null) " — nic zaplanowanego" else " — instaluję ${release.versionName}")
        release ?: return
        prefs.edit { remove(KEY_SCHEDULED) }
        val file = apkFile(release)
        if (!file.isFile || UpdateRules.sha256(file) != release.sha256) {
            updateLog(context, "brak pobranego pliku albo uszkodzony")
            _state.value = UpdateState.Available(release)
            return
        }
        _state.value = UpdateState.Installing(release)
        runCatching { commit(file, atNight = true) }.onFailure {
            updateLog(context, "instalacja nie ruszyła: ${it.message}")
            failed(release, "Instalacja nie ruszyła: ${it.message}")
        }
    }

    /** After a restart of the phone the alarm is gone; the planned version is not. */
    internal fun restoreNight() {
        if (scheduledRelease() != null) {
            planNight()
            updateLog(context, "po restarcie telefonu ponownie zaplanowano noc")
        }
    }

    /** From [UpdateReceiver]: Android refused or the person cancelled. */
    internal fun installFinished(message: String?) {
        val release = when (val current = _state.value) {
            is UpdateState.Installing -> current.release
            is UpdateState.Scheduled -> current.release
            else -> _latest.value
        } ?: return
        _state.value = if (message == null) UpdateState.Available(release) else UpdateState.Failed(release, message)
    }

    /**
     * Android never answers when an install is broken off on its side (e.g. Play Protect's check cancelled): its
     * session is just gone. This app still runs, so the new version did not get in; show that instead of waiting.
     */
    private fun noticeAbandonedInstall() {
        val installing = _state.value as? UpdateState.Installing ?: return
        val sessionId = prefs.getInt(KEY_SESSION, -1)
        if (sessionId < 0 || context.packageManager.packageInstaller.getSessionInfo(sessionId) != null) return
        forgetInstall(context)
        failed(installing.release, "Instalacja nie doszła do skutku. Spróbuj ponownie.")
        openPopup()
    }

    private fun failed(release: AppRelease, message: String) {
        _state.value = UpdateState.Failed(release, message)
    }

    private fun scheduledRelease(): AppRelease? =
        prefs.getString(KEY_SCHEDULED, null)?.let { runCatching { json.decodeFromString<AppRelease>(it) }.getOrNull() }

    private fun apkFile(release: AppRelease) = File(File(context.cacheDir, "update"), "ambify-${release.versionCode}.apk")

    /** The APK in the cache, checked against the published SHA-256; null after a failure (already shown). */
    private suspend fun download(release: AppRelease): File? {
        val file = apkFile(release)
        val ready = withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.isFile && UpdateRules.sha256(file) == release.sha256
        }
        if (ready) return file
        _state.value = UpdateState.Downloading(release, 0f)
        return try {
            withContext(Dispatchers.IO) {
                // Only the version being downloaded stays in the cache.
                file.parentFile?.listFiles()?.forEach { it.delete() }
            }
            api.downloadApk(release, file) { bytes ->
                val progress = if (release.sizeBytes > 0) (bytes.toFloat() / release.sizeBytes).coerceIn(0f, 1f) else 0f
                scope.launch { _state.value = UpdateState.Downloading(release, progress) }
            }
            val sum = withContext(Dispatchers.IO) { UpdateRules.sha256(file) }
            if (sum != release.sha256) {
                file.delete()
                failed(release, "Pobrany plik jest uszkodzony. Spróbuj ponownie.")
                null
            } else {
                file
            }
        } catch (e: ServerError) {
            failed(release, e.message)
            null
        }
    }

    private fun planNight() {
        val at = UpdateRules.nightInstallAt(ZonedDateTime.now(ZoneId.systemDefault())).toInstant().toEpochMilli()
        // Android may move it a little to save battery: at night that is fine.
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, nightIntent())
        updateLog(context, "alarm nocny ustawiony na " + java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.ROOT).format(java.util.Date(at)))
    }

    private fun nightIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_NIGHT,
        Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_NIGHT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Hands the APK to Android. Once AMBiFy has installed one of its own updates, Android lets the next ones in
     * without asking ([PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED]); until then it asks once.
     */
    private fun commit(file: File, atNight: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        }
        val id = installer.createSession(params)
        // Read by [UpdatedReceiver] in the new version (and by [noticeAbandonedInstall] in this one).
        prefs.edit(commit = true) {
            putInt(KEY_SESSION, id)
            putInt(KEY_FROM_VERSION, BuildConfig.VERSION_CODE)
            putBoolean(KEY_OPEN_AFTER, !atNight)
        }
        installer.openSession(id).use { session ->
            session.openWrite("ambify.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val result = Intent(context, UpdateReceiver::class.java)
                .setAction(UpdateReceiver.ACTION_RESULT)
                .putExtra(UpdateReceiver.EXTRA_NIGHT, atNight)
            // Mutable: Android adds the status to it.
            val pending = PendingIntent.getBroadcast(context, id, result, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
            updateLog(context, "przekazano do Androida" + if (atNight) " (noc)" else "")
        }
    }

    companion object {
        private const val PREFS = "updates"
        private const val KEY_CHECKED = "checked_at"
        private const val KEY_SCHEDULED = "scheduled"
        private const val REQUEST_NIGHT = 7001
        private const val KEY_SESSION = "install_session"
        private const val KEY_FROM_VERSION = "install_from_version"
        private const val KEY_OPEN_AFTER = "install_open_after"

        /**
         * In the freshly installed version: was it put in by this updater (not by adb or a store)? Null when not;
         * otherwise whether to open the app now (false for the night install). Forgets the install either way.
         */
        fun takeFinishedInstall(context: Context): Boolean? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val from = prefs.getInt(KEY_FROM_VERSION, -1)
            val openAfter = prefs.getBoolean(KEY_OPEN_AFTER, false)
            forgetInstall(context)
            return if (from in 0 until BuildConfig.VERSION_CODE) openAfter else null
        }

        private fun forgetInstall(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                remove(KEY_SESSION)
                remove(KEY_FROM_VERSION)
                remove(KEY_OPEN_AFTER)
            }
        }

        @Volatile private var instance: AppUpdater? = null

        fun of(context: Context): AppUpdater =
            instance ?: synchronized(this) { instance ?: AppUpdater(context.applicationContext).also { instance = it } }
    }
}
