package pl.ambif.ambify.server.monitor

import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.server.accounts.UserSession
import pl.ambif.ambify.shared.MonitorEventBatch

/**
 * Zapisuje zdarzenia telefonu dziecka do pliku JSONL (jedna linia = jedno zdarzenie),
 * osobny plik per społeczność+użytkownik. Bez bazy i bez ekranu w aplikacji — log czytamy na żądanie
 * wprost z pliku. Katalog [dir] leży poza katalogiem programu, więc deploy.sh go nie nadpisuje.
 */
class MonitorService(private val dir: File, private val clock: () -> Instant = Instant::now) {
    private val json = Json { encodeDefaults = true }

    /** Dopisuje zdarzenia z paczki do pliku dziecka. Pusta paczka nie tworzy pliku. */
    fun record(session: UserSession, batch: MonitorEventBatch) {
        if (batch.events.isEmpty()) return
        val file = fileFor(session.communityId, session.userId)
        file.parentFile?.mkdirs()
        val receivedAt = clock().toString()
        val text = batch.events.joinToString(separator = "\n", postfix = "\n") { event ->
            json.encodeToString(
                StoredEvent.serializer(),
                StoredEvent(
                    device = batch.device,
                    type = event.type.name,
                    at = event.at,
                    detail = event.detail,
                    suspicious = event.suspicious,
                    receivedAt = receivedAt,
                ),
            )
        }
        file.appendText(text)
    }

    /** Plik logu dla dziecka, np. monitor/<społeczność>-<użytkownik>.jsonl. */
    fun fileFor(communityId: String?, userId: String): File =
        dir.resolve("${safe(communityId ?: "brak")}-${safe(userId)}.jsonl")

    // Tylko znaki bezpieczne w nazwie pliku; resztę zamieniamy na "_".
    private fun safe(value: String) = value.replace(Regex("[^0-9A-Za-z._-]"), "_")

    /** Linia logu: zdarzenie z telefonu + czas odebrania na serwerze. */
    @Serializable
    data class StoredEvent(
        val device: String,
        val type: String,
        val at: Long,
        val detail: String,
        val suspicious: Boolean,
        val receivedAt: String,
    )
}
