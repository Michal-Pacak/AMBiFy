package pl.ambif.ambify.modules.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import pl.ambif.ambify.R

/**
 * The night alarm of a planned update, and Android's answers about an install: asking the person (on the screen now,
 * or by a notification when it happens at night), refusing, or the person cancelling.
 */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = AppUpdater.of(context)
        when (intent.action) {
            ACTION_NIGHT -> updater.installScheduled()
            ACTION_RESULT -> when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (intent.getBooleanExtra(EXTRA_NIGHT, false)) notifyReady(context, confirm)
                    else runCatching { context.startActivity(confirm) }.onFailure { notifyReady(context, confirm) }
                }
                // The new version replaces this process; nothing more to do.
                PackageInstaller.STATUS_SUCCESS -> Unit
                PackageInstaller.STATUS_FAILURE_ABORTED -> updater.installFinished(null)
                else -> updater.installFinished(
                    "Android nie zainstalował nowej wersji" +
                        (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { " ($it)" } ?: "") + ".",
                )
            }
        }
    }

    /** Android wants a tap after all (the first own update): the tap opens its "Aktualizować?" question. */
    private fun notifyReady(context: Context, confirm: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Aktualizacje", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nowa wersja AMBiFy gotowa do instalacji")
            .setContentText("Stuknij, aby zainstalować. Dane i logowanie zostają.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val ACTION_NIGHT = "pl.ambif.ambify.update.NIGHT"
        const val ACTION_RESULT = "pl.ambif.ambify.update.RESULT"
        const val EXTRA_NIGHT = "night"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 7002
    }
}
