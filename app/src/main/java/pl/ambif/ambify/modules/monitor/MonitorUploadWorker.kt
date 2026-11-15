package pl.ambif.ambify.modules.monitor

import android.content.Context
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
import pl.ambif.ambify.BuildConfig

/**
 * Build „kids”: zbiera i wysyła zdarzenia w tle, gdy telefon ma sieć — bez otwierania aplikacji.
 *
 * Dlaczego nie wystarcza budzik 23:30: śpiący telefon (Doze) budzi aplikację, ale nie daje jej sieci, więc wysyłka
 * z budzika po cichu przepadała i dane docierały dopiero po ręcznym otwarciu AMBiFy (Realme Filipa, Xiaomi Bartka,
 * 11–13.11.2026). WorkManager z warunkiem „jest sieć” odpala się w oknie, w którym system sieć daje — najpóźniej
 * po wybudzeniu telefonu.
 */
class MonitorUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.KIDS) return Result.success()
        MonitorCollector.collect(applicationContext)
        return if (MonitorCollector.sync(applicationContext)) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "monitor-upload-hourly"
        private const val NOW = "monitor-upload-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Co godzinę (gdy jest sieć). KEEP: kolejne wywołania nie przesuwają już zaplanowanego terminu. */
        fun schedulePeriodic(context: Context) {
            if (!BuildConfig.KIDS) return
            val request = PeriodicWorkRequestBuilder<MonitorUploadWorker>(1, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Jednorazowo, przy najbliższej sieci — po nocnej zbiórce, gdy wysyłka z budzika się nie udała. */
        fun uploadWhenOnline(context: Context) {
            if (!BuildConfig.KIDS) return
            val request = OneTimeWorkRequestBuilder<MonitorUploadWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
