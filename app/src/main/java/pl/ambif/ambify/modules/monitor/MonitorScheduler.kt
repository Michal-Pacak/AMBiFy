package pl.ambif.ambify.modules.monitor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventType

/**
 * Build „kids”: budzi telefon raz w nocy (domyślnie 23:30), żeby zebrać miniony dzień z UsageStats i wysłać na
 * serwer, nawet gdy aplikacja nie była otwierana. Wzór jak [pl.ambif.ambify.modules.timetable.ReminderScheduler].
 * Granica systemu: po „Wymuś zatrzymanie” budzik nie odpali się do następnego ręcznego otwarcia apki — lukę i tak
 * odtworzymy z UsageStats przy kolejnej zbiórce.
 */
object MonitorScheduler {
    const val ACTION_SYNC = "pl.ambif.ambify.monitor.SYNC"
    private const val REQUEST = 7101
    private const val NIGHT_HOUR = 23
    private const val NIGHT_MINUTE = 30

    /** Planuje najbliższą nocną zbiórkę (23:30). Wołane przy otwarciu apki i po każdej zbiórce. */
    fun scheduleNight(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!BuildConfig.KIDS) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextNight(now), pending(context))
    }

    internal fun nextNight(now: ZonedDateTime): Long {
        val today = now.toLocalDate().atTime(NIGHT_HOUR, NIGHT_MINUTE).atZone(now.zone)
        return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant().toEpochMilli()
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, MonitorReceiver::class.java).setAction(ACTION_SYNC),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Odbiera: nocną zbiórkę ([MonitorScheduler.ACTION_SYNC]), zmianę zegara/strefy, instalacje i odinstalowania.
 * Wszystko tylko w buildzie „kids”.
 */
class MonitorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.KIDS) return
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val detail = if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) "zmiana strefy czasowej" else "zmieniono zegar"
                MonitorQueue.of(context).add(listOf(MonitorEvent(MonitorEventType.TIME_CHANGED, System.currentTimeMillis(), detail, suspicious = true)))
            }
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                val removed = intent.action == Intent.ACTION_PACKAGE_REMOVED
                val type = if (removed) MonitorEventType.PACKAGE_REMOVED else MonitorEventType.PACKAGE_ADDED
                val familyLink = pkg.contains("familylink") || pkg == "com.google.android.apps.kids.familylinkhelper"
                MonitorQueue.of(context).add(listOf(MonitorEvent(type, System.currentTimeMillis(), pkg, suspicious = familyLink)))
            }
            MonitorScheduler.ACTION_SYNC -> {
                pl.ambif.ambify.modules.update.updateLog(context, "nocna zbiórka 23:30 — alarm zadziałał")
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        MonitorCollector.collect(context)
                        MonitorCollector.sync(context)
                    } finally {
                        MonitorScheduler.scheduleNight(context) // następna noc
                        pending.finish()
                    }
                }
            }
        }
    }
}
