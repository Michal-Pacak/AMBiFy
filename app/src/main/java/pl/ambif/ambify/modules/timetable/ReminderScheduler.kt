package pl.ambif.ambify.modules.timetable

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.MainActivity
import pl.ambif.ambify.R
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.shared.ReminderKind
import pl.ambif.ambify.shared.TimetableWeek

/** One reminder planned on this phone; kept so it can be planned again after a restart of the phone. */
@Serializable
internal data class PlannedReminder(val id: Int, val at: Long, val kind: ReminderKind, val title: String, val text: String)

/**
 * Plans my reminders of extra activities on this phone: a notification or an alarm on the full screen, a set number
 * of minutes before the start. The server keeps who gets which reminder; the phone asks for this week and the next
 * one and plans those of the signed-in person. Planned again whenever the app opens the timetable or saves an
 * activity, and after the phone restarts (from the saved list), so reminders further ahead come in time.
 */
object ReminderScheduler {
    const val CHANNEL_NOTIFY = "reminders"
    const val CHANNEL_ALARM = "alarms"
    internal const val EXTRA_ID = "id"
    internal const val EXTRA_TITLE = "title"
    internal const val EXTRA_TEXT = "text"
    internal const val EXTRA_KIND = "kind"
    private const val PREFS = "reminders"
    private const val KEY_PLANNED = "planned"
    private val json = Json { ignoreUnknownKeys = true }

    /** Asks the server for this and next week's activities and plans my reminders (the previous ones are replaced). */
    suspend fun refresh(context: Context, api: ServerApi, myId: String) {
        val today = LocalDate.now()
        val weeks = listOf(today, today.plusWeeks(1)).map { api.timetable(it.toString()) }
        plan(context, weeks.flatMap { remindersOf(it, myId) })
    }

    internal fun remindersOf(week: TimetableWeek, myId: String): List<PlannedReminder> {
        val students = week.students.associateBy { it.id }
        val activities = week.activities.associateBy { it.id }
        return week.occurrences.flatMap { occurrence ->
            val activity = activities[occurrence.activityId] ?: return@flatMap emptyList()
            val start = LocalDateTime.of(LocalDate.parse(occurrence.date), LocalTime.parse(activity.startTime))
            activity.reminders.filter { it.memberId == myId }.map { reminder ->
                val at = start.minusMinutes(reminder.minutesBefore.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val who = students[activity.studentId]?.name.orEmpty()
                PlannedReminder(
                    id = "${activity.id}|${occurrence.date}|${reminder.kind}|${reminder.minutesBefore}".hashCode(),
                    at = at,
                    kind = reminder.kind,
                    title = "${activity.name} ${inMinutes(reminder.minutesBefore)}",
                    text = listOfNotNull(who.ifBlank { null }, "${activity.startTime}–${activity.endTime}", activity.place).joinToString(" · "),
                )
            }
        }
    }

    private fun inMinutes(minutes: Int) = when {
        minutes == 0 -> "teraz"
        minutes % 60 == 0 -> "za ${minutes / 60} godz."
        else -> "za $minutes min"
    }

    internal fun plan(context: Context, reminders: List<PlannedReminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load(prefs.getString(KEY_PLANNED, null)).forEach { alarms.cancel(pending(context, it)) }
        val now = System.currentTimeMillis()
        val ahead = reminders.filter { it.at > now }.distinctBy { it.id }
        ahead.forEach { schedule(context, alarms, it) }
        prefs.edit { putString(KEY_PLANNED, json.encodeToString(ahead)) }
    }

    /** After a restart of the phone: the alarms are gone, the saved list is not. */
    internal fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        plan(context, load(prefs.getString(KEY_PLANNED, null)))
    }

    private fun load(saved: String?): List<PlannedReminder> =
        saved?.let { runCatching { json.decodeFromString<List<PlannedReminder>>(it) }.getOrNull() }.orEmpty()

    private fun schedule(context: Context, alarms: AlarmManager, reminder: PlannedReminder) {
        val intent = pending(context, reminder)
        val exact = alarms.canScheduleExactAlarms()
        when {
            reminder.kind == ReminderKind.ALARM && exact -> {
                val show = PendingIntent.getActivity(
                    context, reminder.id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.at, show), intent)
            }
            exact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
            // Without the exact alarm permission Android may deliver it a few minutes late.
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
        }
    }

    private fun pending(context: Context, reminder: PlannedReminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, reminder.id)
            .putExtra(EXTRA_TITLE, reminder.title)
            .putExtra(EXTRA_TEXT, reminder.text)
            .putExtra(EXTRA_KIND, reminder.kind.name)
        return PendingIntent.getBroadcast(context, reminder.id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    internal fun channels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_NOTIFY, "Przypomnienia", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, "Budziki", NotificationManager.IMPORTANCE_HIGH).apply {
                // The alarm screen plays the sound itself, so the notification stays quiet.
                setSound(null, null)
            },
        )
    }

    /** Shows the reminder: a notification, or for an alarm the full-screen [AlarmActivity]. */
    internal fun show(context: Context, id: Int, title: String, text: String, kind: ReminderKind) {
        channels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, if (kind == ReminderKind.ALARM) CHANNEL_ALARM else CHANNEL_NOTIFY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (kind == ReminderKind.ALARM) {
            val alarm = Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val full = PendingIntent.getActivity(context, id, alarm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setCategory(NotificationCompat.CATEGORY_ALARM).setFullScreenIntent(full, true).setOngoing(true).setContentIntent(full)
        } else {
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }
        runCatching { manager.notify(id, builder.build()) }
    }

    /** Snooze from the alarm screen: the same alarm again in [minutes]. */
    internal fun snooze(context: Context, id: Int, title: String, text: String, minutes: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        schedule(context, alarms, PlannedReminder(id, System.currentTimeMillis() + minutes * 60_000L, ReminderKind.ALARM, title, text))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = runCatching { ReminderKind.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_KIND).orEmpty()) }
            .getOrDefault(ReminderKind.NOTIFICATION)
        ReminderScheduler.show(
            context,
            intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0),
            intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty(),
            intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty(),
            kind,
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderScheduler.restore(context)
        pl.ambif.ambify.modules.update.AppUpdater.of(context).restoreNight()
        if (pl.ambif.ambify.BuildConfig.KIDS) {
            pl.ambif.ambify.modules.monitor.MonitorQueue.of(context).add(
                listOf(pl.ambif.ambify.shared.MonitorEvent(pl.ambif.ambify.shared.MonitorEventType.BOOT, System.currentTimeMillis(), "telefon uruchomiony")),
            )
            pl.ambif.ambify.modules.monitor.MonitorScheduler.scheduleNight(context)
        }
    }
}
