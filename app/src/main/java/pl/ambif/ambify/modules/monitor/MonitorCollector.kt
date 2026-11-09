package pl.ambif.ambify.modules.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.TokenStore
import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventBatch
import pl.ambif.ambify.shared.MonitorEventType

/**
 * Build „kids”: zbiera zdarzenia telefonu (użycie aplikacji z UsageStats + zmiany ustawień istotnych dla obchodzenia
 * limitów) i dosyła je na serwer na koncie zalogowanego dziecka. Nic się nie uruchamia, gdy `!BuildConfig.KIDS`.
 * Zbieranie wymaga uprawnienia „dostęp do użycia” (PACKAGE_USAGE_STATS) — bez niego UsageStats zwraca pustkę i po
 * prostu nie ma zdarzeń użycia (zmiany ustawień działają dalej).
 */
object MonitorCollector {
    private const val PREFS = "monitor"
    private const val KEY_USAGE_MARKER = "usage_marker"
    private const val KEY_SNAPSHOT = "settings_snapshot"
    private const val DEFAULT_LOOKBACK_MS = 24 * 60 * 60 * 1000L
    // Separator pól snapshotu (znak sterujący SOH — nie występuje w odczytywanych wartościach ustawień).
    private const val FIELD = "\u0001"

    /** Zbiera nowe zdarzenia od ostatniego znacznika i wrzuca do kolejki. Bezpieczne do częstego wołania. */
    fun collect(context: Context, now: Long = System.currentTimeMillis()) {
        if (!BuildConfig.KIDS) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val events = buildList {
            addAll(usageEvents(context, prefs, now))
            addAll(settingsEvents(context, prefs, now))
        }
        MonitorQueue.of(context).add(events)
    }

    /** Wysyła kolejkę na serwer; po potwierdzeniu usuwa wysłane. Wymaga zalogowania — inaczej zdarzenia czekają. */
    suspend fun sync(context: Context) {
        if (!BuildConfig.KIDS) return
        val queue = MonitorQueue.of(context)
        val pending = queue.peekAll()
        if (pending.isEmpty()) return
        runCatching { authedApi(context).sendMonitorEvents(MonitorEventBatch(device(context), pending)) }
            .onSuccess { queue.removeFirst(pending.size) }
    }

    // UsageStats od ostatniego znacznika: aplikacja na wierzchu i ekran wł/wył. Nadrabia też luki po force-stop.
    private fun usageEvents(context: Context, prefs: SharedPreferences, now: Long): List<MonitorEvent> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val from = prefs.getLong(KEY_USAGE_MARKER, 0L).let { if (it == 0L) now - DEFAULT_LOOKBACK_MS else it }
        val stream = runCatching { manager.queryEvents(from, now) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<MonitorEvent>()
        val event = UsageEvents.Event()
        var last = from
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            if (event.timeStamp > last) last = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    result += MonitorEvent(MonitorEventType.APP_FOREGROUND, event.timeStamp, event.packageName ?: "")
                UsageEvents.Event.SCREEN_INTERACTIVE ->
                    result += MonitorEvent(MonitorEventType.SCREEN, event.timeStamp, "wł")
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    result += MonitorEvent(MonitorEventType.SCREEN, event.timeStamp, "wył")
            }
        }
        prefs.edit { putLong(KEY_USAGE_MARKER, maxOf(last, from)) }
        return result
    }

    // Zmiany ustawień względem poprzedniego odczytu (porównanie w MonitorSettings — testowalne na JVM).
    private fun settingsEvents(context: Context, prefs: SharedPreferences, now: Long): List<MonitorEvent> {
        val current = snapshot(context)
        val old = prefs.getString(KEY_SNAPSHOT, null)?.let { decodeSnapshot(it) }
        prefs.edit { putString(KEY_SNAPSHOT, encodeSnapshot(current)) }
        return MonitorSettings.diff(old, current, now)
    }

    // Snapshot zapisany jako pola rozdzielone SOH (bez kotlinx-serialization — wtyczka jest tylko na :shared).
    private fun encodeSnapshot(s: MonitorSnapshot) = listOf(
        s.autoTime.toString(), s.privateDns, s.safeMode.toString(), s.accessibilityServices, s.devSettings.toString(), s.adb.toString(),
    ).joinToString(FIELD)

    private fun decodeSnapshot(text: String): MonitorSnapshot? {
        val p = text.split(FIELD)
        if (p.size < 6) return null
        return MonitorSnapshot(p[0].toBoolean(), p[1], p[2].toBoolean(), p[3], p[4].toBoolean(), p[5].toBoolean())
    }

    private fun snapshot(context: Context): MonitorSnapshot {
        val cr = context.contentResolver
        fun global(name: String) = runCatching { Settings.Global.getInt(cr, name, 0) }.getOrDefault(0)
        fun globalStr(name: String) = runCatching { Settings.Global.getString(cr, name) }.getOrNull().orEmpty()
        return MonitorSnapshot(
            autoTime = global(Settings.Global.AUTO_TIME) == 1,
            privateDns = "${globalStr("private_dns_mode").ifEmpty { "off" }}:${globalStr("private_dns_specifier")}",
            safeMode = runCatching { context.packageManager.isSafeMode }.getOrDefault(false),
            accessibilityServices = runCatching { Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty(),
            devSettings = global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1,
            adb = global(Settings.Global.ADB_ENABLED) == 1,
        )
    }

    private fun device(context: Context): String {
        val id = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull().orEmpty()
        return "${Build.MODEL} ${id.take(6)}".trim()
    }

    /**
     * ServerApi uwierzytelniony tokenami dziecka z `SharedPreferences("session")` — do wysyłki w tle.
     * Klucze lustrzane do AppSession (jego companion jest private, więc nie da się ich zaimportować).
     */
    private fun authedApi(context: Context): ServerApi {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        val store = object : TokenStore {
            override var accessToken: String?
                get() = prefs.getString("access_token", null)
                set(value) { prefs.edit { putString("access_token", value) } }
            override var refreshToken: String?
                get() = prefs.getString("refresh_token", null)
                set(value) { prefs.edit { putString("refresh_token", value) } }

            override fun onSessionExpired() {}
        }
        return ServerApi(tokens = store, community = { prefs.getString("community", null) })
    }
}
