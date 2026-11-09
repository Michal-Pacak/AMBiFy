package pl.ambif.ambify.shared

import kotlinx.serialization.Serializable

/**
 * Zapis zdarzeń z telefonu dziecka (build "kids"): telefon dosyła zdarzenia, serwer dopisuje je do pliku logu.
 * To działanie tymczasowe — bez tabeli w bazie i bez ekranu w aplikacji; log analizujemy na żądanie wprost z pliku.
 */
object MonitorPaths {
    const val EVENTS = "/monitor/events"
}

/** Rodzaj zdarzenia — zapisujemy "co", nie treść wiadomości ani tego, co dziecko pisze. */
enum class MonitorEventType {
    APP_FOREGROUND, // aplikacja na wierzchu
    SCREEN,         // ekran włączony/wyłączony
    TIME_CHANGED,   // ręczna zmiana zegara
    AUTO_TIME,      // stan "czas automatyczny" (wyłączenie to typowe obejście)
    SAFE_MODE,      // tryb awaryjny
    PACKAGE_ADDED,  // instalacja aplikacji
    PACKAGE_REMOVED,// odinstalowanie aplikacji
    ACCESSIBILITY,  // zmiana usług dostępności (tędy wyłącza się nadzór Family Link)
    DEV_SETTINGS,   // opcje programisty / ADB
    PRIVATE_DNS,    // zmiana Prywatnego DNS
    VPN,            // VPN włączony/wyłączony
    APP_STOPPED,    // wykryty force-stop aplikacji (luka w zdarzeniach)
    BOOT,           // start telefonu
    OTHER,
}

/** Pojedyncze zdarzenie z telefonu. */
@Serializable
data class MonitorEvent(
    val type: MonitorEventType,
    /** Czas zdarzenia na telefonie (epoch millis). */
    val at: Long,
    /** Krótki opis "co": nazwa pakietu, "cofnięto o 2 h" itp. Nigdy treść. */
    val detail: String = "",
    /** Oznaczone jako podejrzane (obejście limitów). */
    val suspicious: Boolean = false,
)

/** Paczka zdarzeń z jednego telefonu — wysyłana okresowo i w nocy; offline czeka w kolejce. */
@Serializable
data class MonitorEventBatch(
    /** Identyfikator telefonu (model + krótki skrót), żeby rozróżnić urządzenia dziecka. */
    val device: String,
    val events: List<MonitorEvent>,
)
