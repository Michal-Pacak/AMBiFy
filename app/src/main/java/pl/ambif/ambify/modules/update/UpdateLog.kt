package pl.ambif.ambify.modules.update

import android.content.Context
import pl.ambif.ambify.BuildConfig
import pl.ambif.ambify.modules.monitor.MonitorQueue
import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventType

/**
 * Build „kids”: zapisuje kroki samoaktualizacji (plan nocny, alarm, odpowiedź Androida) do logu zdarzeń wysyłanego
 * na serwer — żeby z serwerka było widać, co się stało w nocy na telefonie dziecka. W innych buildach nic nie robi.
 */
internal fun updateLog(context: Context, text: String) {
    if (!BuildConfig.KIDS) return
    runCatching {
        MonitorQueue.of(context).add(
            listOf(MonitorEvent(MonitorEventType.OTHER, System.currentTimeMillis(), "aktualizacja (${BuildConfig.VERSION_NAME}): $text")),
        )
    }
}
