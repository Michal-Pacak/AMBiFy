package pl.ambif.ambify.modules.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.MainActivity
import pl.ambif.ambify.R

/**
 * Runs in the new version right after AMBiFy updated itself (Android ends the old one without a word).
 * Opens the app again and says the update is done; the notification stays when Android does not let the app
 * open itself from the background. Only for updates started by [AppUpdater] — not adb or a store.
 * In Google Play this job goes to its in-app update (`completeUpdate()` restarts the app by itself).
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // The night install: nobody is looking, the app opens as usual next time.
        if (AppUpdater.takeFinishedInstall(context) != true) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        notifyDone(context, open)
        // May be refused as a start from the background; then the notification above does the job.
        runCatching { context.startActivity(open) }
    }

    private fun notifyDone(context: Context, open: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Aktualizacja zakończona", NotificationManager.IMPORTANCE_HIGH))
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AMBiFy ${BuildConfig.VERSION_NAME} zainstalowana")
            .setContentText("Stuknij, aby otworzyć.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL = "updates_done"
        private const val NOTIFICATION_ID = 7003

        /** The app is on the screen: the "zainstalowana" notification has done its job. */
        fun clearNotice(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
