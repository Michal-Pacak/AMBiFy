package pl.ambif.ambify.features.lists.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import pl.ambif.ambify.MainActivity
import pl.ambif.ambify.R
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.TokenStore

/**
 * Lists in the background: changes made offline go out as soon as the phone has a network (also with the app
 * closed), and every ~15 minutes the copies are refreshed. A new conflict on a list the person created, and a
 * conflict settled, are told with a notification (the list's creator decides — Lists canvas, board 9).
 */
class ListsSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val api = backgroundApi(applicationContext) ?: return Result.success()
        var offline = false
        for (communityId in ListRepository.communities(applicationContext)) {
            val repo = ListRepository.of(applicationContext, api, communityId)
            val events = repo.sync().toMutableList()
            if (repo.status.value is SyncStatus.Offline) offline = true
            // 0.7.3: photos and files go once their items are on the server.
            val files = ListFiles.of(applicationContext, api, communityId)
            if (files.hasPending && !repo.hasPending && repo.status.value is SyncStatus.Synced) {
                val sent = files.upload { title, text -> events += ListsEvent(ListsEvent.Kind.REFUSED, title, text) }
                if (sent) repo.sync()
            }
            if (files.hasPending) offline = true
            val me = repo.people.value.me
            val lists = repo.state.value.view.lists
            events.forEach { event ->
                val mine = lists.firstOrNull { it.id == event.listId }?.createdBy == me
                when (event.kind) {
                    ListsEvent.Kind.CONFLICT -> if (mine) notify(applicationContext, event)
                    ListsEvent.Kind.RESOLVED, ListsEvent.Kind.REFUSED -> notify(applicationContext, event)
                    ListsEvent.Kind.QUEUED -> Unit
                }
            }
        }
        return if (offline) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "lists-sync-periodic"
        private const val NOW = "lists-sync-now"
        private const val CHANNEL = "lists"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes with a network. KEEP: calling it again does not move the planned time. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListsSyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Once, as soon as there is a network: changes are waiting. */
        fun syncWhenOnline(context: Context) {
            val request = OneTimeWorkRequestBuilder<ListsSyncWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }

        private fun notify(context: Context, event: ListsEvent) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Listy", NotificationManager.IMPORTANCE_DEFAULT))
            val id = (event.conflictId ?: event.title + event.text).hashCode()
            val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(event.title)
                .setContentText(event.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(event.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(id, notification)
        }

        /** Signed in with the app's saved tokens (the keys of AppSession); null when signed out. */
        private fun backgroundApi(context: Context): ServerApi? {
            val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
            if (prefs.getString("refresh_token", null) == null) return null
            val store = object : TokenStore {
                override var accessToken: String?
                    get() = prefs.getString("access_token", null)
                    set(value) { prefs.edit { putString("access_token", value) } }
                override var refreshToken: String?
                    get() = prefs.getString("refresh_token", null)
                    set(value) { prefs.edit { putString("refresh_token", value) } }

                // The app ends the session itself the next time it is opened.
                override fun onSessionExpired() {}
            }
            return ServerApi(tokens = store, community = { prefs.getString("community", null) })
        }
    }
}
