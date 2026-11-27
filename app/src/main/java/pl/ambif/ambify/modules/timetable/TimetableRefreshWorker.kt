package pl.ambif.ambify.modules.timetable

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.TokenStore
import pl.ambif.ambify.shared.Recurrences

/**
 * B-014: every night after the server has read the school diaries (4:00), the phone's copy of this week and the
 * next is refreshed, so the morning shows today's plan even before the app is opened, and without a network.
 * Android may run it a little later than [AT] (battery saving); opening the Dashboard refreshes it anyway.
 */
class TimetableRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
        if (prefs.getString("refresh_token", null) == null) return Result.success()
        val monday = Recurrences.mondayOf(java.time.LocalDate.now())
        var failed = false
        for (communityId in TimetableCache.communities(applicationContext)) {
            val api = backgroundApi(prefs, communityId)
            for (week in listOf(monday, monday.plusWeeks(1))) {
                runCatching { api.timetable(week.toString()) }
                    .onSuccess { TimetableCache.save(applicationContext, communityId, it) }
                    .onFailure { failed = true }
            }
        }
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "timetable-refresh-night"
        /** Half an hour after the server's 4:00 update of the diaries. */
        private val AT: LocalTime = LocalTime.of(4, 30)

        /** Once a day around [AT], with a network. KEEP: calling it again does not move the planned time. */
        fun schedule(context: Context) {
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<TimetableRefreshWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayToNext(LocalDateTime.now()).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        internal fun delayToNext(now: LocalDateTime): Duration {
            var next = now.toLocalDate().atTime(AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        private fun backgroundApi(prefs: android.content.SharedPreferences, communityId: String): ServerApi {
            val store = object : TokenStore {
                override var accessToken: String?
                    get() = prefs.getString("access_token", null)
                    set(value) { prefs.edit().putString("access_token", value).commit() }
                override var refreshToken: String?
                    get() = prefs.getString("refresh_token", null)
                    set(value) { prefs.edit().putString("refresh_token", value).commit() }
                override fun onSessionExpired() {}
            }
            return ServerApi(tokens = store, community = { communityId })
        }
    }
}
