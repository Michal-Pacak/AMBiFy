package pl.ambif.ambify.modules.monitor

import pl.ambif.ambify.shared.MonitorEvent
import pl.ambif.ambify.shared.MonitorEventType

/**
 * Stan ustawień istotnych dla obchodzenia limitów, odczytany w jednym momencie. Odczyt z systemu jest w kolektorze
 * (zależny od Androida); tutaj trzymamy sam stan i czyste porównanie, żeby dało się je przetestować na JVM.
 */
data class MonitorSnapshot(
    /** Czas automatyczny włączony (wyłączenie to typowe obejście — ręczne cofanie zegara). */
    val autoTime: Boolean,
    /** Tryb + nazwa prywatnego DNS, np. "hostname:bartosz.dns.nextdns.io" albo "off". */
    val privateDns: String,
    val safeMode: Boolean,
    /** Lista włączonych usług dostępności (tędy wyłącza się nadzór Family Link). */
    val accessibilityServices: String,
    /** Opcje programisty włączone. */
    val devSettings: Boolean,
    /** Debugowanie USB/ADB włączone. */
    val adb: Boolean,
)

object MonitorSettings {
    /**
     * Zdarzenia wynikające ze zmiany ustawień między [old] a [current].
     * Pierwszy odczyt ([old] == null) nic nie zgłasza — nie znamy jeszcze punktu odniesienia.
     */
    fun diff(old: MonitorSnapshot?, current: MonitorSnapshot, at: Long): List<MonitorEvent> {
        if (old == null) return emptyList()
        val events = mutableListOf<MonitorEvent>()
        if (old.autoTime && !current.autoTime) {
            events += MonitorEvent(MonitorEventType.AUTO_TIME, at, "wyłączono czas automatyczny", suspicious = true)
        } else if (!old.autoTime && current.autoTime) {
            events += MonitorEvent(MonitorEventType.AUTO_TIME, at, "włączono czas automatyczny")
        }
        if (old.privateDns != current.privateDns) {
            events += MonitorEvent(MonitorEventType.PRIVATE_DNS, at, "prywatny DNS: ${current.privateDns}", suspicious = true)
        }
        if (!old.safeMode && current.safeMode) {
            events += MonitorEvent(MonitorEventType.SAFE_MODE, at, "tryb awaryjny włączony", suspicious = true)
        }
        if (old.accessibilityServices != current.accessibilityServices) {
            events += MonitorEvent(MonitorEventType.ACCESSIBILITY, at, "zmiana usług dostępności", suspicious = true)
        }
        if (!old.devSettings && current.devSettings) {
            events += MonitorEvent(MonitorEventType.DEV_SETTINGS, at, "włączono opcje programisty", suspicious = true)
        }
        if (!old.adb && current.adb) {
            events += MonitorEvent(MonitorEventType.DEV_SETTINGS, at, "włączono debugowanie USB/ADB", suspicious = true)
        }
        return events
    }
}
